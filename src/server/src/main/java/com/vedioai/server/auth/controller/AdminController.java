package com.vedioai.server.auth.controller;

import com.vedioai.server.auth.dto.AdminUserPageView;
import com.vedioai.server.auth.dto.AdminUserView;
import com.vedioai.server.auth.exception.SessionExpiredException;
import com.vedioai.server.auth.service.AdminUserService;
import com.vedioai.server.auth.service.ReauthenticationService;
import com.vedioai.server.common.api.ApiResponse;
import com.vedioai.server.common.error.InvalidRequestException;
import com.vedioai.server.common.identity.CurrentActor;
import com.vedioai.server.common.identity.CurrentActorProvider;
import com.vedioai.server.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@Validated
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");

    private final AdminUserService adminUserService;
    private final CurrentActorProvider currentActorProvider;
    private final ReauthenticationService reauthenticationService;

    public AdminController(
            AdminUserService adminUserService,
            CurrentActorProvider currentActorProvider,
            ReauthenticationService reauthenticationService) {

        this.adminUserService = adminUserService;
        this.currentActorProvider = currentActorProvider;
        this.reauthenticationService = reauthenticationService;
    }

    @GetMapping("/users")
    public ApiResponse<AdminUserPageView> users(
            @RequestParam(defaultValue = "1")
            @Min(1)
            int page,
            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int pageSize,
            @RequestParam(required = false)
            String status,
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        if (status != null && !STATUSES.contains(status)) {
            throw new InvalidRequestException("status 必须为 ACTIVE 或 DISABLED");
        }
        return new ApiResponse<>(
                adminUserService.list(page, pageSize, status),
                TraceIdFilter.traceId(request));
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<AdminUserView> changeStatus(
            @PathVariable
            @NotBlank
            @Size(max = 128)
            String userId,
            @Valid @RequestBody StatusRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        CurrentActor actor = currentActorProvider.requireCurrentActor();
        requireRecent(request);
        AdminUserView view = adminUserService.changeStatus(
                actor.userId(),
                userId,
                body.status(),
                body.expectedVersion());
        return new ApiResponse<>(view, TraceIdFilter.traceId(request));
    }

    @PutMapping("/users/{userId}/roles")
    public ApiResponse<AdminUserView> changeRoles(
            @PathVariable
            @NotBlank
            @Size(max = 128)
            String userId,
            @Valid @RequestBody RolesRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        CurrentActor actor = currentActorProvider.requireCurrentActor();
        requireRecent(request);
        AdminUserView view = adminUserService.changeRoles(
                actor.userId(),
                userId,
                body.roles(),
                body.expectedVersion());
        return new ApiResponse<>(view, TraceIdFilter.traceId(request));
    }

    private void requireRecent(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new SessionExpiredException();
        }
        reauthenticationService.require(session);
    }

    public record StatusRequest(
            @NotBlank
            @Pattern(regexp = "ACTIVE|DISABLED", message = "status 必须为 ACTIVE 或 DISABLED")
            String status,
            @NotNull
            @Min(1)
            Long expectedVersion) {
    }

    public record RolesRequest(
            @NotEmpty
            @Size(min = 1, max = 2)
            List<
                    @Pattern(regexp = "USER|ADMIN", message = "角色只能为 USER 或 ADMIN")
                    String> roles,
            @NotNull
            @Min(1)
            Long expectedVersion) {
    }
}
