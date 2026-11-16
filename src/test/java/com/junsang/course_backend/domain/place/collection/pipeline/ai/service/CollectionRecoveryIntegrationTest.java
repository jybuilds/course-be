package com.junsang.course_backend.domain.place.collection.pipeline.ai.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junsang.course_backend.domain.place.collection.pipeline.orchestration.PlaceCollectionRestartRecovery;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.KakaoCollectionJobWriter;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCollectionProfileRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.repository.PlaceCollectionJobRepository;
import com.junsang.course_backend.domain.place.repository.AreaRepository;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.infra.openai.batch.OpenAiBatchClient;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** DB_URL이 전용 테스트 DB일 때만 실행한다. 운영 DB에서는 이 테스트가 실행되지 않는다. */
@SpringBootTest(properties={"place-collection.scheduling.enabled=false", "place-collection.recovery.single-instance=true"})
@EnabledIfEnvironmentVariable(named="DB_URL",matches=".*course_recovery_test.*")
class CollectionRecoveryIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OpenAiBatchJobWriter jobs;
    @Autowired OpenAiBatchTaggingService service;
    @Autowired PlaceCollectionRestartRecovery recovery;
    @Autowired KakaoCollectionJobWriter kakao;
    @Autowired PlaceCollectionProfileRepository profiles;
    @Autowired PlaceCollectionJobRepository kakaoJobs;
    @Autowired AreaRepository areas;
    @Autowired PlaceCollectionTempRepository temps;
    @MockitoBean OpenAiBatchClient client;
    @MockitoSpyBean AiBatchResultWriter results;
    final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE ai_batch_tagging_items, place_tags, places, place_collection_temp, openai_batch_tagging_jobs, place_collection_jobs RESTART IDENTITY CASCADE");
    }
    @AfterEach void cleanupTrigger() { jdbc.execute("DROP FUNCTION IF EXISTS test_reject_place_tag() CASCADE"); jdbc.execute("DROP FUNCTION IF EXISTS test_reject_kakao_child() CASCADE"); jdbc.execute("DROP FUNCTION IF EXISTS test_reject_batch_receipt() CASCADE"); }
    Long temp(String id) {
        return jdbc.queryForObject("INSERT INTO place_collection_temp(area_id,provider,provider_place_id,default_place_type,name,latitude,longitude,processing_step,status) "
            + "SELECT id,'KAKAO',?,'CAFE',?,37.55,126.95,'AI_TAGGING','PENDING' FROM areas ORDER BY id LIMIT 1 RETURNING id",Long.class,id,"fixture-"+id);
    }
    OpenAiBatchJobWriter.BatchClaim claim() { var c=jobs.claimPending(100); jobs.markSubmitted(c.jobId(),"remote-"+c.jobId()); return c; }
    String place(Long id,int weight) { return "{\"tempId\":"+id+",\"placeType\":\"CAFE\",\"keywords\":[],\"priorityScores\":{\"experience\":2,\"distinctiveness\":3},\"tags\":[{\"code\":\"CAFE\",\"weight\":"+weight+"}]}"; }
    void output(OpenAiBatchJobWriter.BatchClaim c, String ... values) throws Exception {
        String key=jobs.items(c.jobId()).getFirst().requestKey();
        String line=mapper.writeValueAsString(Map.of("custom_id",key,"response",Map.of("status_code",200,
            "body",Map.of("output_text","{\"results\":["+String.join(",",values)+"]}"))));
        when(client.poll("remote-"+c.jobId(),jobs.find(c.jobId()).getProvider())).thenReturn(new OpenAiBatchClient.BatchPoll("completed",line,null));
    }
    String state(Long id) { return jdbc.queryForObject("SELECT status FROM place_collection_temp WHERE id=?",String.class,id); }

    @Test void commitsSuccessAndRollsBackOnlyPlaceWhoseTagInsertFails() throws Exception {
        Long a=temp("a"),b=temp("b"); var c=claim(); output(c,place(a,99),place(b,90));
        jdbc.execute("CREATE FUNCTION test_reject_place_tag() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.weight=99 THEN RAISE EXCEPTION 'synthetic tag insert failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_tag_failure BEFORE INSERT ON place_tags FOR EACH ROW EXECUTE FUNCTION test_reject_place_tag()");
        service.collect(c.jobId());
        assertThat(state(a)).isEqualTo("FAILED"); assertThat(state(b)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM places",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT error_code FROM place_collection_temp WHERE id=?",String.class,a)).isEqualTo("AI_STORAGE_FAILED");
        assertThat(jdbc.queryForObject("SELECT next_retry_at IS NOT NULL FROM place_collection_temp WHERE id=?",Boolean.class,a)).isTrue();
    }
    static class ProcessStopped extends Error { }
    @Test void resumesAfterFirstPlaceCommitWithoutOverwritingThatPlace() throws Exception {
        Long a=temp("a"),b=temp("b"); var c=claim(); output(c,place(a,90),place(b,80));
        AtomicBoolean firstCrash=new AtomicBoolean(true);
        doAnswer(invocation->{
            var result=(com.junsang.course_backend.domain.place.collection.pipeline.ai.dto.AiTaggingResultBatch.AiTaggingResult)invocation.getArgument(2);
            if(result.tempId().equals(b) && firstCrash.getAndSet(false)) throw new ProcessStopped();
            return invocation.callRealMethod();
        }).when(results).complete(any(),any(),any(),any(),any());
        assertThatThrownBy(()->service.collect(c.jobId())).isInstanceOf(ProcessStopped.class);
        assertThat(state(a)).isEqualTo("COMPLETED"); assertThat(state(b)).isEqualTo("PROCESSING");
        jdbc.update("UPDATE places SET name='operator-preserved' WHERE provider_place_id='a'");
        jdbc.update("UPDATE openai_batch_tagging_jobs SET collect_token='stopped-process',collect_lease_until=CURRENT_TIMESTAMP+INTERVAL '15 minutes' WHERE id=?",c.jobId());
        recovery.run(new DefaultApplicationArguments());
        service.collect(c.jobId());
        assertThat(state(b)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM places",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT name FROM places WHERE provider_place_id='a'",String.class)).isEqualTo("operator-preserved");
        assertThat(jobs.find(c.jobId()).getStatus().name()).isEqualTo("COMPLETED");
        // 모든 장소 저장 후 Job 완료 직전 종료된 경우도 결과를 다시 반영하지 않는다.
        jdbc.update("UPDATE openai_batch_tagging_jobs SET status='SUBMITTED' WHERE id=?",c.jobId());
        service.collect(c.jobId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM places",Integer.class)).isEqualTo(2);
    }
    @Test void onlyOneConcurrentCollectorAcquiresJob() throws Exception {
        temp("a"); var c=claim(); var pool=Executors.newFixedThreadPool(2);
        try {
            var outcomes=pool.invokeAll(List.of(()->jobs.acquireCollection(c.jobId()),()->jobs.acquireCollection(c.jobId())));
            int count=0; for(var outcome:outcomes) if(outcome.get()!=null) count++;
            assertThat(count).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void kakaoPersistsAllRootsAndChildrenBeforeExecutionAndReusesInterruptedRun() {
        var area=areas.findAll().getFirst();
        var ps=profiles.findByProviderAndIsActiveTrueOrderById(PlaceProvider.KAKAO).subList(0,2);
        var roots=kakao.initialize(List.of(area),ps);
        assertThat(roots).hasSize(2);
        var started=kakao.tryStart(roots.getFirst().getId());
        var children=kakao.split(started.getId(),false);
        assertThat(children).hasSize(4);
        assertThat(kakaoJobs.findByParentJobIdOrderByIdAsc(started.getId())).hasSize(4);
        kakao.tryStart(children.getFirst().getId());
        recovery.run(new DefaultApplicationArguments());
        var resumed=kakao.initialize(List.of(area),ps);
        assertThat(resumed).extracting(v->v.getId()).containsExactlyElementsOf(roots.stream().map(v->v.getId()).toList());
        assertThat(kakao.tryStart(children.getFirst().getId())).isNotNull();
        assertThat(kakaoJobs.count()).isEqualTo(6);
    }
    @Test void naverRestartRecoversOnlyProcessingAndLeavesCompletedAiStageAlone() {
        Long a=temp("a"),b=temp("b");
        jdbc.update("UPDATE place_collection_temp SET processing_step='NAVER_ENRICHMENT',status='PROCESSING' WHERE id=?",a);
        jdbc.update("UPDATE place_collection_temp SET status='COMPLETED' WHERE id=?",b);
        recovery.run(new DefaultApplicationArguments());
        assertThat(state(a)).isEqualTo("PENDING"); assertThat(state(b)).isEqualTo("COMPLETED");
        assertThat(temps.findNaverRecoveryTargets(java.time.LocalDateTime.now(),org.springframework.data.domain.PageRequest.of(0,10)))
            .extracting(v->v.getId()).containsExactly(a);
    }
    @Test void failedPlacesAloneAreReclaimedWhenTheirRetryBecomesDue() throws Exception {
        Long a=temp("a"),b=temp("b"); var c=claim(); output(c,place(a,90)); service.collect(c.jobId());
        assertThat(state(a)).isEqualTo("COMPLETED"); assertThat(state(b)).isEqualTo("FAILED");
        assertThat(jobs.claimRetries(10)).isNull();
        jdbc.update("UPDATE place_collection_temp SET next_retry_at=CURRENT_TIMESTAMP-INTERVAL '1 day' WHERE id=?",b);
        var retry=jobs.claimRetries(10);
        assertThat(retry.targets()).extracting(v->v.getId()).containsExactly(b);
    }
    @Test void kakaoSplitRollsBackAllChildrenAndParentStateOnInsertFailure() {
        var area=areas.findAll().getFirst();
        var ps=profiles.findByProviderAndIsActiveTrueOrderById(PlaceProvider.KAKAO).subList(0,1);
        var root=kakao.initialize(List.of(area),ps).getFirst(); kakao.tryStart(root.getId());
        jdbc.execute("CREATE FUNCTION test_reject_kakao_child() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.parent_job_id IS NOT NULL AND NEW.min_latitude>"+root.getMinLatitude()+" THEN RAISE EXCEPTION 'synthetic child failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_kakao_failure BEFORE INSERT ON place_collection_jobs FOR EACH ROW EXECUTE FUNCTION test_reject_kakao_child()");
        assertThatThrownBy(()->kakao.split(root.getId(),false)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(kakaoJobs.findByParentJobIdOrderByIdAsc(root.getId())).isEmpty();
        assertThat(kakaoJobs.findById(root.getId()).orElseThrow().getStatus().name()).isEqualTo("RUNNING");
    }
    @Test void manifestGroupsExactIdsIntoTenPlaceRequests() {
        for(int i=0;i<23;i++) temp("manifest-"+i);
        var c=claim();
        var groups=jobs.items(c.jobId()).stream().collect(java.util.stream.Collectors.groupingBy(OpenAiBatchJobWriter.Item::requestKey));
        assertThat(groups.values().stream().map(List::size).sorted().toList()).containsExactly(3,10,10);
        assertThat(jobs.items(c.jobId())).extracting(OpenAiBatchJobWriter.Item::tempId).doesNotHaveDuplicates();
    }

    @Test void crashBeforeReceiptPersistenceBecomesUnknownWithoutAutomaticResubmission() {
        Long id=temp("unacknowledged"); var claim=jobs.claimPending(10);
        recovery.run(new DefaultApplicationArguments());
        assertThat(jobs.find(claim.jobId()).getErrorCode().name()).isEqualTo("OPENAI_BATCH_SUBMISSION_UNKNOWN");
        assertThat(state(id)).isEqualTo("FAILED"); assertThat(jobs.claimRetries(10)).isNull();
        assertThat(jobs.items(claim.jobId())).hasSize(1);
    }
    @Test void acceptedRemoteBatchWithFailedLocalReceiptWriteDoesNotResubmit() {
        Long id=temp("receipt-failure");
        when(client.submit(anyString(),anyString())).thenReturn("remote-accepted");
        jdbc.execute("CREATE FUNCTION test_reject_batch_receipt() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='SUBMITTED' THEN RAISE EXCEPTION 'synthetic receipt persistence failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER test_receipt_failure BEFORE UPDATE ON openai_batch_tagging_jobs FOR EACH ROW EXECUTE FUNCTION test_reject_batch_receipt()");
        assertThatThrownBy(()->service.submitPending(10)).isInstanceOf(RuntimeException.class);
        assertThat(state(id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM place_collection_temp WHERE id=?",String.class,id)).isEqualTo("OPENAI_BATCH_SUBMISSION_UNKNOWN");
        assertThat(jobs.claimRetries(10)).isNull();
        verify(client,times(1)).submit(anyString(),anyString());
    }

}
