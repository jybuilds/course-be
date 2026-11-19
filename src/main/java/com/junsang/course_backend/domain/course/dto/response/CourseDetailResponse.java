package com.junsang.course_backend.domain.course.dto.response;

import com.junsang.course_backend.domain.course.entity.Course;
import com.junsang.course_backend.domain.course.entity.CourseItem;
import com.junsang.course_backend.domain.place.entity.CourseTag;
import com.junsang.course_backend.domain.place.entity.Place;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/// 대표 코스 상세 화면에 필요한 앵커, 방문 순서, 코스 태그다.
public record CourseDetailResponse(
        Long courseId,
        String title,
        String description,
        CityResponse city,
        AreaResponse area,
        PlaceResponse anchorPlace,
        List<ScheduleItemResponse> scheduleItems,
        List<CourseTagResponse> courseTags
) {
    public static CourseDetailResponse of(Course course) {
        return new CourseDetailResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                CityResponse.from(course.getArea()),
                AreaResponse.from(course.getArea()),
                PlaceResponse.from(course.getAnchorPlace()),
                course.getScheduleItems().stream().map(ScheduleItemResponse::from).toList(),
                // 가중치가 높은 태그가 코스를 더 잘 설명하므로 그 순서로 내려준다.
                course.getTags().stream()
                        .sorted(Comparator.comparingInt(CourseTag::getWeight).reversed())
                        .map(CourseTagResponse::from)
                        .toList()
        );
    }

    public record CityResponse(Long cityId, String code, String name) {
        private static CityResponse from(com.junsang.course_backend.domain.place.entity.Area area) {
            return new CityResponse(
                    area.getCity().getId(), area.getCity().getCode(), area.getCity().getName());
        }
    }

    public record AreaResponse(Long areaId, String code, String name) {
        private static AreaResponse from(com.junsang.course_backend.domain.place.entity.Area area) {
            return new AreaResponse(area.getId(), area.getCode(), area.getName());
        }
    }

    public record ScheduleItemResponse(int itemOrder, String role, PlaceResponse place) {
        private static ScheduleItemResponse from(CourseItem item) {
            return new ScheduleItemResponse(
                    item.getItemOrder(), item.getRole().name(), PlaceResponse.from(item.getPlace()));
        }
    }

    /// 상세 화면에서 지도와 길찾기에 쓰도록 좌표와 외부 링크를 함께 준다.
    public record PlaceResponse(
            Long placeId,
            String name,
            String placeType,
            String addressName,
            String roadAddressName,
            BigDecimal latitude,
            BigDecimal longitude,
            String placeUrl
    ) {
        private static PlaceResponse from(Place place) {
            return new PlaceResponse(
                    place.getId(),
                    place.getName(),
                    place.getPlaceType().name(),
                    place.getAddressName(),
                    place.getRoadAddressName(),
                    place.getLatitude(),
                    place.getLongitude(),
                    place.getPlaceUrl()
            );
        }
    }

    public record CourseTagResponse(String code, String displayName, int weight) {
        private static CourseTagResponse from(CourseTag courseTag) {
            return new CourseTagResponse(
                    courseTag.getTag().getCode(), courseTag.getTag().getDisplayName(), courseTag.getWeight());
        }
    }
}
