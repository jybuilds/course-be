package com.junsang.course_backend.domain.place.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/// 대표 태그에 속한 유사 사용자 선택지다.
@Entity
@Table(name = "tag_options")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class TagOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Column(name = "option_name", nullable = false, length = 100)
    private String optionName;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    public static TagOption create(Tag tag, String optionName, int displayOrder) {
        TagOption option = new TagOption();
        option.tag = tag;
        option.optionName = optionName;
        option.displayOrder = displayOrder;
        option.isActive = true;
        return option;
    }
}
