ALTER TABLE academic_class_sections
    ADD COLUMN weekday SMALLINT,
    ADD COLUMN starts_at TIME,
    ADD COLUMN ends_at TIME,
    ADD COLUMN room VARCHAR(32);

ALTER TABLE academic_class_sections
    ADD CONSTRAINT ck_academic_section_schedule CHECK (
        (weekday IS NULL AND starts_at IS NULL AND ends_at IS NULL AND room IS NULL) OR
        (weekday BETWEEN 1 AND 7 AND starts_at IS NOT NULL AND ends_at IS NOT NULL AND ends_at > starts_at
            AND (room IS NULL OR char_length(btrim(room, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 32))
    );
