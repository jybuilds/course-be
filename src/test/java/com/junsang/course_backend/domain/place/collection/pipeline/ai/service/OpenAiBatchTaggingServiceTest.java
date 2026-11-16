package com.junsang.course_backend.domain.place.collection.pipeline.ai.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.service.AiTaggingInputBuilder;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResultBatch.AiTaggingResult;
import com.junsang.course_backend.domain.place.collection.pipeline.ai.entity.OpenAiBatchTaggingJob;
import com.junsang.course_backend.domain.place.collection.entity.PlaceRefinementErrorCode;
import com.junsang.course_backend.domain.place.repository.TagRepository;
import com.junsang.course_backend.infra.openai.batch.OpenAiBatchClient;
import com.junsang.course_backend.infra.openai.config.AiProviderProperties;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

class OpenAiBatchTaggingServiceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final TagRepository tagRepository = mock(TagRepository.class);
    final OpenAiBatchClient client = mock(OpenAiBatchClient.class);
    final OpenAiBatchJobWriter jobs = mock(OpenAiBatchJobWriter.class);
    final AiBatchResultWriter results = mock(AiBatchResultWriter.class);
    final AiTaggingErrorHandler errors = new AiTaggingErrorHandler(jobs);
    final AiTaggingInputBuilder input = mock(AiTaggingInputBuilder.class);
    final OpenAiBatchTaggingService service;
    OpenAiBatchTaggingServiceTest() {
        var settings = new AiProviderProperties.ProviderSettings(URI.create("http://mock.invalid"), "test", "test", null, null);
        service = new OpenAiBatchTaggingService(tagRepository, client,
                new AiProviderProperties("OPENAI", settings, settings), mapper, jobs, input, results, errors);
        var job = OpenAiBatchTaggingJob.submitting(2);
        job.configureProvider("OMNIROUTE", "submitted-model"); job.submit("remote-1");
        when(jobs.acquireCollection(1L)).thenReturn("owner");
        when(jobs.find(1L)).thenReturn(job);
        when(jobs.items(1L)).thenReturn(List.of(new OpenAiBatchJobWriter.Item(10L,"places-10-20","PENDING"),
                new OpenAiBatchJobWriter.Item(20L,"places-10-20","PENDING")));
        when(input.findActiveRules()).thenReturn(List.of());
    }
    String place(long id) { return "{\"tempId\":"+id+",\"placeType\":\"CAFE\",\"keywords\":[],\"priorityScores\":{\"experience\":3,\"distinctiveness\":0},\"tags\":[{\"code\":\"CAFE\",\"weight\":90}]}"; }
    String line(String text) throws Exception {
        return mapper.writeValueAsString(Map.of("custom_id","places-10-20", "response", Map.of("status_code",200,
                "body",Map.of("output_text",text))));
    }
    void output(String status, String text) { when(client.poll("remote-1","OMNIROUTE")).thenReturn(new OpenAiBatchClient.BatchPoll(status,text,null)); }
    @Test void usesSubmittedProviderAndKeepsPollingActiveBatch() {
        output("in_progress", ""); assertThat(service.collect(1L)).isFalse();
        verify(client).poll("remote-1","OMNIROUTE"); verifyNoInteractions(results);
        verify(jobs).releaseCollection(1L,"owner");
    }
    @Test void oneStorageFailureDoesNotBlockFollowingPlace() throws Exception {
        output("completed",line("{\"results\":["+place(10)+","+place(20)+"]}"));
        doThrow(new DataAccessResourceFailureException("synthetic")).when(results).complete(eq(1L),eq("owner"),argThat(v->v.tempId()==10),any(),any());
        service.collect(1L);
        verify(results).complete(eq(1L),eq("owner"),argThat(v->v.tempId()==20),any(),any());
        verify(jobs).failItem(eq(1L),eq("owner"),eq(10L),argThat(f->f.code()==PlaceRefinementErrorCode.AI_STORAGE_FAILED));
    }
    @Test void validatesEachScoreIndependently() throws Exception {
        String broken = place(10).replace("\"experience\":3", "\"experience\":1000");
        output("completed",line("{\"results\":["+broken+","+place(20)+"]}")); service.collect(1L);
        verify(results).complete(eq(1L),eq("owner"),argThat(v->v.tempId()==20),any(),any());
        verify(jobs).failItem(eq(1L),eq("owner"),eq(10L),argThat(f->f.code()==PlaceRefinementErrorCode.AI_SCORE_VALIDATION_FAILED));
    }
    @Test void refusesCrossRequestAndDuplicateIdsBeforeStorage() throws Exception {
        output("completed",line("{\"results\":["+place(10)+","+place(10)+","+place(999)+"]}"));
        assertThat(service.collect(1L)).isFalse(); verifyNoInteractions(results);
        verify(jobs,atLeastOnce()).failItem(eq(1L),eq("owner"),eq(10L),argThat(f->f.code()==PlaceRefinementErrorCode.AI_RESPONSE_DUPLICATE));
        verify(jobs).failItem(eq(1L),eq("owner"),eq(20L),argThat(f->f.code()==PlaceRefinementErrorCode.AI_RESPONSE_MISSING));
    }
    @ParameterizedTest @ValueSource(strings={"expired","cancelled"})
    void recoversSuccessFromTerminalBatch(String status) throws Exception {
        output(status,line("{\"results\":["+place(10)+"]}")); service.collect(1L);
        verify(results).complete(eq(1L),eq("owner"),argThat(v->v.tempId()==10),any(),any());
        verify(jobs).failPending(eq(1L),eq("owner"),argThat(f->f.code().name().equals("AI_BATCH_"+status.toUpperCase())));
    }
    @Test void handlesTopLevelQuotaError() {
        output("completed","{\"custom_id\":\"places-10-20\",\"error\":{\"code\":\"usage_limit_reached\",\"resets_at\":4102444800}}");
        service.collect(1L);
        verify(jobs).failItem(eq(1L),eq("owner"),eq(10L),argThat(f->f.code()==PlaceRefinementErrorCode.AI_API_QUOTA_EXHAUSTED && f.retryHint()!=null));
    }
    @Test void simultaneousCollectorDoesNotFetchRemoteAgain() {
        when(jobs.acquireCollection(1L)).thenReturn(null);
        assertThat(service.collect(1L)).isFalse(); verifyNoInteractions(client);
    }
    @Test void lostLeaseDoesNotMarkUnprocessedPlacesFailed() throws Exception {
        output("completed",line("{\"results\":["+place(10)+"]}"));
        doThrow(new OpenAiBatchJobWriter.CollectionLeaseLostException()).when(jobs).renewCollection(1L,"owner");
        assertThatThrownBy(()->service.collect(1L)).isInstanceOf(OpenAiBatchJobWriter.CollectionLeaseLostException.class);
        verify(jobs,never()).failPending(any(),anyString(),any());
    }
    @Test void pollsTransientFailuresWithoutChangingTargetState() {
        when(client.poll(any(),any())).thenThrow(new org.springframework.web.client.ResourceAccessException("synthetic"));
        assertThatThrownBy(()->service.collect(1L)).isInstanceOf(RuntimeException.class);
        verify(jobs,never()).failPending(any(),anyString(),any()); verify(jobs).releaseCollection(1L,"owner");
    }
    @Test void authenticationFailurePausesPollingWithoutResubmittingPlaces() {
        when(client.poll(any(),any())).thenThrow(new org.springframework.web.client.RestClientResponseException("test",401,"",null,null,null));
        assertThat(service.collect(1L)).isFalse();
        verify(jobs).pausePoll(eq(1L),eq("owner"),argThat(f->f.code()==PlaceRefinementErrorCode.AI_API_AUTH_FAILED));
        verify(jobs,never()).failPending(any(),anyString(),any());
    }
    @Test void preparationFailureDoesNotLeaveClaimWithoutErrorHandling() {
        when(jobs.claimPending(10)).thenReturn(new OpenAiBatchJobWriter.BatchClaim(1L,List.of()));
        when(tagRepository.findByIsActiveTrueOrderByDisplayOrderAsc()).thenThrow(new DataAccessResourceFailureException("synthetic"));
        assertThatThrownBy(()->service.submitPending(10)).isInstanceOf(RuntimeException.class);
        verify(jobs).fail(eq(1L),argThat(f->f.code()==PlaceRefinementErrorCode.AI_STORAGE_FAILED));
        verifyNoInteractions(client);
    }
    @Test void malformedRequestResultDoesNotBlockAnotherRequest() throws Exception {
        when(jobs.items(1L)).thenReturn(List.of(new OpenAiBatchJobWriter.Item(10L,"places-10-10","PENDING"),
                new OpenAiBatchJobWriter.Item(20L,"places-20-20","PENDING")));
        output("completed",line("bad-json").replace("places-10-20","places-10-10")+"\n"+
                line("{\"results\":["+place(20)+"]}").replace("places-10-20","places-20-20"));
        service.collect(1L);
        verify(results).complete(eq(1L),eq("owner"),argThat(v->v.tempId()==20),any(),any());
        verify(jobs).failItem(eq(1L),eq("owner"),eq(10L),argThat(f->f.code()==PlaceRefinementErrorCode.AI_RESPONSE_PARSE_FAILED));
    }

}
