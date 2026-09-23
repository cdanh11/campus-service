package com.campus.identity.domain;

public record UserAccountSearch(int page, int size, String query, AccountStatus status, RoleCode role, String sortField, boolean ascending) { }
