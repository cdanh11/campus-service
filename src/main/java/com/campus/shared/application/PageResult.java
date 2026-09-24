package com.campus.shared.application;

import java.util.List;

public record PageResult<T>(List<T> content, long totalElements) { }
