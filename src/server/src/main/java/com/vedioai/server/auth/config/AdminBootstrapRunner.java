package com.vedioai.server.auth.config;

import com.vedioai.server.auth.dto.AdminUserView;
import com.vedioai.server.auth.service.AdminUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;


/**
 * Explicit, default-disabled local bootstrap for the first administrator.
 * Enable only for one controlled startup, then disable again.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final VedioaiAuthProperties properties;
    private final AdminUserService adminUserService;

    public AdminBootstrapRunner(
            VedioaiAuthProperties properties,
            AdminUserService adminUserService) {

        this.properties = properties;
        this.adminUserService = adminUserService;
    }

    @Override
    public void run(ApplicationArguments args) {
        VedioaiAuthProperties.AdminBootstrap bootstrap = properties.getAdminBootstrap();
        if (!bootstrap.isEnabled()) {
            return;
        }

        String targetUserId = bootstrap.getUserId();
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalStateException(
                    "vedioai.auth.admin-bootstrap.user-id is required when bootstrap is enabled");
        }

        AdminUserView user = adminUserService.bootstrapAdmin(targetUserId.trim());
        log.info("admin_bootstrap_success userId={}", user.userId());
        log.info("admin_bootstrap_complete disable vedioai.auth.admin-bootstrap.enabled and restart");
    }
}
