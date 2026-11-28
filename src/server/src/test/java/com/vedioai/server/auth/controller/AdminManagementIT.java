package com.vedioai.server.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vedioai.server.auth.exception.LastAdminException;
import com.vedioai.server.auth.service.AdminUserService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;


class AdminManagementIT extends IdentityIntegrationTestBase {

    @Autowired
    private AdminUserService adminUserService;

    @BeforeEach
    @AfterEach
    void removePreviousTestAdministrators() {
        jdbcTemplate.update(
                """
                DELETE ur FROM auth_user_role ur
                JOIN auth_user u ON u.id = ur.user_id
                JOIN auth_role r ON r.id = ur.role_id
                WHERE r.role_code = 'ADMIN'
                  AND u.email_norm LIKE 'test+%@example.invalid'
                """);
    }

    @Test
    void bootstrapAndAdminManagementProtectRolesStatusAndVersions() throws Exception {
        RegisteredUser admin = registerUser("管理员");
        promoteToAdmin(admin);

        RegisteredUser normal = registerUser("普通用户");
        TestAccountClient adminClient = loggedIn(admin.email(), admin.password());
        TestAccountClient normalClient = loggedIn(normal.email(), normal.password());

        TestAccountClient.Response forbidden = normalClient.get("/api/admin/users");
        assertEquals(403, forbidden.status(), forbidden.body());

        TestAccountClient.Response page = adminClient.get("/api/admin/users?page=1&pageSize=20");
        assertEquals(200, page.status(), page.body());
        assertTrue(page.body().contains("***@example.invalid"));

        TestAccountClient.Response withoutReauth = adminClient.patch(
                "/api/admin/users/" + normal.userId() + "/status",
                "{\"status\":\"DISABLED\",\"expectedVersion\":1}");
        assertEquals(403, withoutReauth.status(), withoutReauth.body());
        assertTrue(withoutReauth.body().contains("REAUTH_REQUIRED"));

        assertEquals(200, adminClient.post(
                "/api/auth/reauthentication",
                "{\"password\":\"" + admin.password() + "\"}").status());

        TestAccountClient.Response staleStatus = adminClient.patch(
                "/api/admin/users/" + normal.userId() + "/status",
                "{\"status\":\"DISABLED\",\"expectedVersion\":99}");
        assertEquals(409, staleStatus.status(), staleStatus.body());

        TestAccountClient.Response disabled = adminClient.patch(
                "/api/admin/users/" + normal.userId() + "/status",
                "{\"status\":\"DISABLED\",\"expectedVersion\":1}");
        assertEquals(200, disabled.status(), disabled.body());
        assertTrue(disabled.body().contains("\"status\":\"DISABLED\""));
        long disabledVersion = TestAccountClient.jsonLong(disabled.body(), "version");

        assertEquals(401, normalClient.get("/api/auth/sessions").status());
        assertEquals(401, normalClient.me().status());
        TestAccountClient disabledLogin = new TestAccountClient(port);
        assertEquals(403, disabledLogin.login(normal.email(), normal.password()).status());

        TestAccountClient.Response enabled = adminClient.patch(
                "/api/admin/users/" + normal.userId() + "/status",
                "{\"status\":\"ACTIVE\",\"expectedVersion\":" + disabledVersion + "}");
        assertEquals(200, enabled.status(), enabled.body());
        long enabledVersion = TestAccountClient.jsonLong(enabled.body(), "version");

        TestAccountClient.Response promoted = adminClient.put(
                "/api/admin/users/" + normal.userId() + "/roles",
                "{\"roles\":[\"USER\",\"ADMIN\"],\"expectedVersion\":" + enabledVersion + "}");
        assertEquals(200, promoted.status(), promoted.body());
        assertTrue(promoted.body().contains("\"ADMIN\""));

        TestAccountClient promotedClient = loggedIn(normal.email(), normal.password());
        TestAccountClient.Response promotedAdminPage = promotedClient.get("/api/admin/users");
        assertEquals(200, promotedAdminPage.status(), promotedAdminPage.body());

        long adminVersion = TestAccountClient.jsonLong(adminClient.me().body(), "version");
        TestAccountClient.Response self = adminClient.patch(
                "/api/admin/users/" + admin.userId() + "/status",
                "{\"status\":\"DISABLED\",\"expectedVersion\":" + adminVersion + "}");
        assertEquals(409, self.status(), self.body());
        assertTrue(self.body().contains("SELF_MODIFICATION"));

        assertThrows(IllegalStateException.class,
                () -> adminUserService.bootstrapAdmin(normal.userId()));
    }

    @Transactional
    @Test
    void lastActiveAdministratorCannotBeDisabled() throws Exception {
        jdbcTemplate.update(
                "DELETE FROM auth_user_role WHERE role_id = "
                        + "(SELECT id FROM auth_role WHERE role_code = 'ADMIN')");
        RegisteredUser onlyAdmin = registerUser("唯一管理员");
        adminUserService.bootstrapAdmin(onlyAdmin.userId());
        RegisteredUser actor = registerUser("非管理员操作人");

        Long version = jdbcTemplate.queryForObject(
                "SELECT version FROM auth_user WHERE user_uid = ?",
                Long.class,
                onlyAdmin.userId());

        assertThrows(LastAdminException.class, () -> adminUserService.changeStatus(
                databaseUserId(actor.userId()),
                onlyAdmin.userId(),
                "DISABLED",
                version));
    }

    @Transactional
    @Test
    void lastActiveAdministratorCannotLoseAdminRole() throws Exception {
        jdbcTemplate.update(
                "DELETE FROM auth_user_role WHERE role_id = "
                        + "(SELECT id FROM auth_role WHERE role_code = 'ADMIN')");
        RegisteredUser onlyAdmin = registerUser("唯一管理员");
        adminUserService.bootstrapAdmin(onlyAdmin.userId());
        RegisteredUser actor = registerUser("非管理员操作人");

        Long version = jdbcTemplate.queryForObject(
                "SELECT version FROM auth_user WHERE user_uid = ?",
                Long.class,
                onlyAdmin.userId());

        assertThrows(LastAdminException.class, () -> adminUserService.changeRoles(
                databaseUserId(actor.userId()),
                onlyAdmin.userId(),
                List.of("USER"),
                version));
    }

    private void promoteToAdmin(RegisteredUser user) {
        List<Long> activeAdminIds = jdbcTemplate.queryForList(
                """
                SELECT u.id
                FROM auth_user u
                JOIN auth_user_role ur ON ur.user_id = u.id
                JOIN auth_role r ON r.id = ur.role_id
                WHERE r.role_code = 'ADMIN' AND u.status = 'ACTIVE'
                ORDER BY u.id
                LIMIT 1
                """,
                Long.class);

        if (activeAdminIds.isEmpty()) {
            adminUserService.bootstrapAdmin(user.userId());
            return;
        }

        Long version = jdbcTemplate.queryForObject(
                "SELECT version FROM auth_user WHERE user_uid = ?",
                Long.class,
                user.userId());
        adminUserService.changeRoles(
                activeAdminIds.get(0),
                user.userId(),
                List.of("USER", "ADMIN"),
                version);
    }
}
