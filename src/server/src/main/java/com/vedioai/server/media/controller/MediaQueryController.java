package com.vedioai.server.media.controller;

import com.vedioai.server.common.api.ApiResponse;
import com.vedioai.server.common.identity.CurrentActor;
import com.vedioai.server.common.identity.CurrentActorProvider;
import com.vedioai.server.common.web.TraceIdFilter;
import com.vedioai.server.media.dto.MediaDetailView;
import com.vedioai.server.media.dto.MediaPageView;
import com.vedioai.server.media.service.MediaQueryService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/media")
public class MediaQueryController {

    private final MediaQueryService mediaQueryService;
    private final CurrentActorProvider currentActorProvider;

    public MediaQueryController(
            MediaQueryService mediaQueryService,
            CurrentActorProvider currentActorProvider) {

        this.mediaQueryService = mediaQueryService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    public ApiResponse<MediaPageView> list(
            @RequestParam(required = false)
            String q,
            @RequestParam(required = false)
            String status,
            @RequestParam(defaultValue = "1")
            @Min(1)
            @Max(1_000_000)
            int page,
            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int pageSize,
            @RequestParam(defaultValue = "CREATED_DESC")
            String sort,
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        CurrentActor actor = currentActorProvider.requireCurrentActor();
        return new ApiResponse<>(
                mediaQueryService.list(actor.ownerId(), q, status, page, pageSize, sort),
                TraceIdFilter.traceId(request));
    }

    @GetMapping("/{mediaId}")
    public ApiResponse<MediaDetailView> detail(
            @PathVariable
            @Size(min = 1, max = 128)
            String mediaId,
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        CurrentActor actor = currentActorProvider.requireCurrentActor();
        return new ApiResponse<>(
                mediaQueryService.detail(actor.ownerId(), mediaId),
                TraceIdFilter.traceId(request));
    }
}