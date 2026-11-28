package com.vedioai.server.auth.dto;

import java.util.List;


public record AdminUserPageView(
        List<AdminUserView> items,
        int page,
        int pageSize,
        long total) {
}
