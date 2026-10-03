package com.campus.academic.api.delivery;

import java.util.List;
import com.campus.shared.application.PageResult;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    static <T> PageResponse<T> from(PageResult<T> result, int page, int size) {
        return new PageResponse<>(result.content(), page, size, result.totalElements(), (int) Math.ceil((double) result.totalElements() / size));
    }
}
