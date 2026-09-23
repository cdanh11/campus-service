package com.campus.identity.domain;

import java.util.List;

public record UserAccountPage(List<UserAccount> content, long totalElements) { }
