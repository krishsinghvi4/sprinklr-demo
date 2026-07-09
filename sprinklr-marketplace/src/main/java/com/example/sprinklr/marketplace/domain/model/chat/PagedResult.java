package com.example.sprinklr.marketplace.domain.model.chat;

import java.util.List;

public record PagedResult<T>(
        List<T> items,
        int page,
        int pageSize,
        long totalCount,
        boolean hasMore
) {
}
