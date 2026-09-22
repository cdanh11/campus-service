package com.campus.identity.infrastructure.persistence;

import com.campus.identity.domain.AdminGuardRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminGuardPersistenceAdapter implements AdminGuardRepository {
    private final JdbcTemplate jdbcTemplate;

    AdminGuardPersistenceAdapter(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public void lock() {
        jdbcTemplate.queryForObject("SELECT guard_id FROM identity_admin_guard WHERE guard_id = 1 FOR UPDATE", Short.class);
    }
}
