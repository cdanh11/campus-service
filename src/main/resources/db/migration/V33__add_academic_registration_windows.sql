ALTER TABLE academic_terms
    ADD COLUMN registration_opens_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN registration_closes_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE academic_terms
    ADD CONSTRAINT ck_academic_terms_registration_window CHECK (
        (registration_opens_at IS NULL AND registration_closes_at IS NULL) OR
        (registration_opens_at IS NOT NULL AND registration_closes_at IS NOT NULL
            AND registration_closes_at > registration_opens_at)
    );
