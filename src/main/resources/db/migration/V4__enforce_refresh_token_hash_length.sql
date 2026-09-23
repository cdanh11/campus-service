ALTER TABLE identity_refresh_tokens
    ADD CONSTRAINT ck_identity_refresh_tokens_token_hash_length
    CHECK (octet_length(token_hash) = 32);
