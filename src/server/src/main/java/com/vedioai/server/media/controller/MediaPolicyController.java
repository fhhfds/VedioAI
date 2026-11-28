package com.vedioai.server.media.controller;

import com.vedioai.server.common.api.ApiResponse;
import com.vedioai.server.common.web.TraceIdFilter;
import com.vedioai.server.media.dto.MediaPolicyView;
import com.vedioai.server.media.service.MediaPolicyService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class MediaPolicyController {

    private final MediaPolicyService mediaPolicyService;

    public MediaPolicyController(MediaPolicyService mediaPolicyService) {
        this.mediaPolicyService = mediaPolicyService;
    }

    @GetMapping("/policy")
    public ApiResponse<MediaPolicyView> policy(
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        return new ApiResponse<>(
                mediaPolicyService.policy(),
                TraceIdFilter.traceId(request));
    }
}