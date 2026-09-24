package com.campus.organization.domain;

public record OrganizationUnitSearch(int page, int size, String query, OrganizationUnitStatus status,
                                     String sortField, boolean ascending) { }
