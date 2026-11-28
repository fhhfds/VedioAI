package com.vedioai.server.media.dto;

import java.util.List;

public record MediaPageView(
        List<MediaSummaryView> items,
        int page,
        int pageSize,
        long total) {
}