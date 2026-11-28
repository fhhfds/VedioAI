package com.vedioai.server.media.service;

import java.util.Set;

import com.vedioai.server.common.error.InvalidRequestException;
import com.vedioai.server.common.error.ResourceNotFoundException;
import com.vedioai.server.media.dto.MediaDetailView;
import com.vedioai.server.media.dto.MediaPageView;
import com.vedioai.server.media.repository.MediaQueryRepository;

import org.springframework.stereotype.Service;

@Service
public class MediaQueryService {

    private static final Set<String> FILTERABLE_STATUSES = Set.of(
            "PROBE_PENDING", "READY", "FAILED");
    private static final Set<String> SORTS = Set.of("CREATED_DESC", "NAME_ASC");

    private final MediaQueryRepository mediaQueryRepository;

    public MediaQueryService(MediaQueryRepository mediaQueryRepository) {
        this.mediaQueryRepository = mediaQueryRepository;
    }

    public MediaPageView list(
            String ownerId,
            String query,
            String status,
            int page,
            int pageSize,
            String sort) {

        String normalizedQuery = normalizeQuery(query);
        String normalizedStatus = normalizeStatus(status);
        String normalizedSort = normalizeSort(sort);

        MediaQueryRepository.Page result = mediaQueryRepository.list(
                ownerId,
                normalizedQuery,
                normalizedStatus,
                page,
                pageSize,
                normalizedSort);

        return new MediaPageView(result.items(), page, pageSize, result.total());
    }

    public MediaDetailView detail(String ownerId, String mediaId) {
        return mediaQueryRepository.find(ownerId, mediaId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > 100) {
            throw new InvalidRequestException("q 最长 100 个字符");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!FILTERABLE_STATUSES.contains(normalized)) {
            throw new InvalidRequestException("status 必须为 PROBE_PENDING、READY 或 FAILED");
        }
        return normalized;
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "CREATED_DESC";
        }
        String normalized = sort.trim().toUpperCase();
        if (!SORTS.contains(normalized)) {
            throw new InvalidRequestException("sort 必须为 CREATED_DESC 或 NAME_ASC");
        }
        return normalized;
    }
}