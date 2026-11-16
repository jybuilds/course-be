package com.junsang.course_backend.domain.place.collection.pipeline.kakao.service;

import com.junsang.course_backend.domain.place.entity.Area;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCategoryRule;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.entity.PlaceCollectionJob;
import com.junsang.course_backend.domain.place.collection.entity.PlaceCollectionTemp;
import com.junsang.course_backend.domain.place.entity.PlaceProvider;
import com.junsang.course_backend.domain.place.entity.PlaceType;
import com.junsang.course_backend.domain.place.repository.AreaRepository;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.LegalDongAreaResolver;
import com.junsang.course_backend.domain.place.collection.pipeline.kakao.service.PlaceTypeClassifier;
import com.junsang.course_backend.domain.place.collection.repository.PlaceCollectionTempRepository;
import com.junsang.course_backend.infra.kakao.dto.response.KakaoPlaceDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KakaoPlaceWriter {
    private final JdbcTemplate jdbc;
    private final AreaRepository areaRepository;
    private final PlaceCollectionTempRepository tempRepository;
    private final LegalDongAreaResolver legalDongAreaResolver;
    private final PlaceTypeClassifier placeTypeClassifier;
    // 카카오 응답을 법정동 기준 Area에 연결하고 Temp에 생성 또는 갱신한다.
    @org.springframework.transaction.annotation.Transactional
    public void saveDocuments(
            List<KakaoPlaceDocument> documents,
            PlaceCollectionJob job,
            List<PlaceCategoryRule> rules
    ) {
        // 중첩 구역의 신규 공급자 ID 삽입 경쟁을 페이지 저장 동안만 직렬화한다.
        jdbc.execute("SELECT pg_advisory_xact_lock(739001)");
        List<ResolvedDocument> resolvedDocuments = documents.stream()
                .map(document -> new ResolvedDocument(
                        document,
                        legalDongAreaResolver.resolveAreaCode(document.addressName())
                ))
                .filter(ResolvedDocument::isCollectable)
                .toList();
        if (resolvedDocuments.isEmpty()) {
            return;
        }

        // 한 페이지의 Area·기존 Temp를 한 번에 읽어 장소마다 발생하던 조회 N+1을 막는다.
        Map<String, Area> areasByCode = findAreasByCode(resolvedDocuments);
        Map<String, PlaceCollectionTemp> tempsByProviderPlaceId = findTempsByProviderPlaceId(resolvedDocuments);
        List<PlaceCollectionTemp> tempsToSave = new ArrayList<>();
        for (ResolvedDocument resolved : resolvedDocuments) {
            KakaoPlaceDocument document = resolved.document();
            Area area = areasByCode.get(resolved.areaCode());
            if (area == null) {
                throw new IllegalStateException("카카오 주소에 매핑된 Area를 찾을 수 없습니다: " + resolved.areaCode());
            }
            PlaceType classification = placeTypeClassifier.classifyForCollection(
                    job.getPlaceType(),
                    document.categoryName(),
                    document.placeName(),
                    rules
            );
            PlaceCollectionTemp temp = tempsByProviderPlaceId.get(document.id());
            if (temp != null) {
                temp.refreshKakao(
                        area,
                        classification,
                        document.placeName(),
                        document.addressName(),
                        document.roadAddressName(),
                        document.categoryName(),
                        document.categoryGroupCode(),
                        document.y(),
                        document.x(),
                        document.placeUrl(),
                        document.phone()
                );
            } else {
                temp = PlaceCollectionTemp.create(
                        area,
                        PlaceProvider.KAKAO,
                        document.id(),
                        classification,
                        document.placeName(),
                        document.addressName(),
                        document.roadAddressName(),
                        document.categoryName(),
                        document.categoryGroupCode(),
                        document.y(),
                        document.x(),
                        document.placeUrl(),
                        document.phone()
                );
                tempsByProviderPlaceId.put(document.id(), temp);
            }
            tempsToSave.add(temp);
        }
        tempRepository.saveAll(tempsToSave);
    }

    // 페이지에 등장한 Area 코드만 IN 조회해 주소마다 Area를 다시 조회하지 않는다.
    private Map<String, Area> findAreasByCode(List<ResolvedDocument> documents) {
        return areaRepository.findByCodeIn(distinctAreaCodes(documents)).stream()
                .collect(Collectors.toMap(Area::getCode, Function.identity()));
    }

    // 카카오 provider ID 목록으로 기존 Temp를 한 번에 조회한다.
    private Map<String, PlaceCollectionTemp> findTempsByProviderPlaceId(List<ResolvedDocument> documents) {
        return tempRepository.findByProviderAndProviderPlaceIdIn(
                        PlaceProvider.KAKAO,
                        distinctProviderPlaceIds(documents)
                )
                .stream()
                .collect(Collectors.toMap(PlaceCollectionTemp::getProviderPlaceId, Function.identity()));
    }

    private Set<String> distinctAreaCodes(List<ResolvedDocument> documents) {
        return documents.stream()
                .map(ResolvedDocument::areaCode)
                .collect(Collectors.toSet());
    }

    private Set<String> distinctProviderPlaceIds(List<ResolvedDocument> documents) {
        return documents.stream()
                .map(resolved -> resolved.document().id())
                .collect(Collectors.toSet());
    }

    private record ResolvedDocument(KakaoPlaceDocument document, String areaCode) {

        private boolean isCollectable() {
            return areaCode != null
                    && document.id() != null
                    && document.x() != null
                    && document.y() != null;
        }
    }
}
