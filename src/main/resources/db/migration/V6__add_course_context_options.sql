CREATE TABLE course_companion_types (
    course_id BIGINT NOT NULL,
    companion_type VARCHAR(20) NOT NULL,
    CONSTRAINT fk_course_companion_types_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT pk_course_companion_types PRIMARY KEY (course_id, companion_type),
    CONSTRAINT ck_course_companion_types_value CHECK (companion_type IN ('FRIEND', 'LOVER', 'FAMILY'))
);

CREATE TABLE course_time_slots (
    course_id BIGINT NOT NULL,
    time_slot VARCHAR(20) NOT NULL,
    CONSTRAINT fk_course_time_slots_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT pk_course_time_slots PRIMARY KEY (course_id, time_slot),
    CONSTRAINT ck_course_time_slots_value CHECK (time_slot IN ('MORNING', 'AFTERNOON', 'NIGHT'))
);
