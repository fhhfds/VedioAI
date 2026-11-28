package com.vedioai.server.media.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import com.vedioai.server.auth.controller.IdentityIntegrationTestBase;
import com.vedioai.server.auth.controller.TestAccountClient;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

class MediaQueryIT extends IdentityIntegrationTestBase {

    @Test
    void mediaApisRequireLoginAndPolicyIsAvailable() throws Exception {
        TestAccountClient anonymous = new TestAccountClient(port);
        assertEquals(401, anonymous.get("/api/media").status());
        assertEquals(401, anonymous.get("/api/media/policy").status());

        RegisteredUser user = registerUser("素材查询用户");
        TestAccountClient client = loggedIn(user.email(), user.password());

        TestAccountClient.Response policy = client.get("/api/media/policy");
        assertEquals(200, policy.status(), policy.body());
        assertTrue(policy.body().contains("\"policyVersion\":\"media-policy-v1\""));
        assertTrue(policy.body().contains("\"chunkSizeBytes\":5242880"));

        TestAccountClient.Response empty = client.get("/api/media");
        assertEquals(200, empty.status(), empty.body());
        assertTrue(empty.body().contains("\"items\":[]"));
        assertTrue(empty.body().contains("\"total\":0"));
    }

    @Test
    void listAndDetailAreIsolatedByOwner() throws Exception {
        RegisteredUser owner = registerUser("素材所有者");
        RegisteredUser other = registerUser("其他用户");
        TestAccountClient ownerClient = loggedIn(owner.email(), owner.password());
        TestAccountClient otherClient = loggedIn(other.email(), other.password());

        String readyId = createMedia(owner.userId(), "课程素材.mp4", "READY", 3000L, 1920, 1080, null);
        String pendingId = createMedia(owner.userId(), "待探测素材.mp4", "PROBE_PENDING", null, null, null, null);
        createMedia(owner.userId(), "失败素材.mp4", "FAILED", null, null, null, "PROBE_FAILED");
        String deletedId = createMedia(owner.userId(), "已删除素材.mp4", "DELETED", null, null, null, null);

        TestAccountClient.Response ownerList = ownerClient.get("/api/media?page=1&pageSize=20");
        assertEquals(200, ownerList.status(), ownerList.body());
        assertTrue(ownerList.body().contains(readyId));
        assertTrue(ownerList.body().contains(pendingId));
        assertFalse(ownerList.body().contains(deletedId));
        assertTrue(ownerList.body().contains("\"total\":3"));

        TestAccountClient.Response otherList = otherClient.get("/api/media?page=1&pageSize=20");
        assertEquals(200, otherList.status(), otherList.body());
        assertTrue(otherList.body().contains("\"total\":0"));
        assertFalse(otherList.body().contains(readyId));

        assertEquals(404, otherClient.get("/api/media/" + readyId).status());
        assertEquals(404, ownerClient.get("/api/media/" + deletedId).status());

        TestAccountClient.Response detail = ownerClient.get("/api/media/" + readyId);
        assertEquals(200, detail.status(), detail.body());
        assertTrue(detail.body().contains("\"status\":\"READY\""));
        assertTrue(detail.body().contains("\"PLAY\""));
        assertTrue(detail.body().contains("\"DELETE\""));
        assertTrue(detail.body().contains("\"width\":1920"));
        assertTrue(detail.body().contains("\"durationMs\":3000"));

        TestAccountClient.Response pending = ownerClient.get("/api/media/" + pendingId);
        assertEquals(200, pending.status(), pending.body());
        assertTrue(pending.body().contains("\"durationMs\":null"));
        assertTrue(pending.body().contains("\"deletion\":{\"canDelete\":false"));
        assertTrue(pending.body().contains("\"processingJobId\":"));
        assertNotNull(TestAccountClient.jsonString(pending.body(), "processingJobId"));
    }

    @Test
    void listSupportsFilterSearchSortAndPagination() throws Exception {
        RegisteredUser user = registerUser("素材筛选用户");
        TestAccountClient client = loggedIn(user.email(), user.password());

        createMedia(user.userId(), "B 课程.mp4", "READY", 1000L, 640, 360, null);
        createMedia(user.userId(), "A 课程.mp4", "READY", 2000L, 1280, 720, null);
        createMedia(user.userId(), "其他.mp4", "FAILED", null, null, null, "BAD_MEDIA");

        TestAccountClient.Response filtered = client.get("/api/media?q=课程&status=READY&page=1&pageSize=1&sort=NAME_ASC");
        assertEquals(200, filtered.status(), filtered.body());
        assertTrue(filtered.body().contains("\"total\":2"));
        assertTrue(filtered.body().contains("A 课程.mp4"));
        assertFalse(filtered.body().contains("其他.mp4"));
        assertTrue(filtered.body().contains("\"pageSize\":1"));

        TestAccountClient.Response failed = client.get("/api/media?status=FAILED");
        assertEquals(200, failed.status(), failed.body());
        assertTrue(failed.body().contains("\"total\":1"));
        assertTrue(failed.body().contains("其他.mp4"));

        TestAccountClient.Response invalidStatus = client.get("/api/media?status=DELETED");
        assertEquals(400, invalidStatus.status(), invalidStatus.body());
        assertTrue(invalidStatus.body().contains("INVALID_ARGUMENT"));

        TestAccountClient.Response invalidSort = client.get("/api/media?sort=RANDOM");
        assertEquals(400, invalidSort.status(), invalidSort.body());
    }

    private String createMedia(
            String ownerId,
            String displayName,
            String status,
            Long durationMs,
            Integer width,
            Integer height,
            String failureCode) {

        jdbcTemplate.update(
                """
                INSERT INTO owner_media_guard (owner_id) VALUES (?)
                ON DUPLICATE KEY UPDATE owner_id = owner_id
                """,
                ownerId);

        String uploadUid = "upl_test_" + UUID.randomUUID().toString().replace("-", "");
        KeyHolder uploadKey = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO upload_session
                        (upload_uid, owner_id, original_name, declared_content_type,
                         expected_size_bytes, chunk_size_bytes, total_parts,
                         policy_version, policy_snapshot, status, expires_at)
                    VALUES
                        (?, ?, ?, 'video/mp4', 100, 50, 2,
                         'media-policy-v1', JSON_OBJECT('fixture', true), 'COMPLETED',
                         DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY))
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, uploadUid);
            statement.setString(2, ownerId);
            statement.setString(3, displayName);
            return statement;
        }, uploadKey);
        Number uploadId = uploadKey.getKey();
        assertNotNull(uploadId);

        String mediaUid = "med_test_" + UUID.randomUUID().toString().replace("-", "");
        KeyHolder mediaKey = new GeneratedKeyHolder();
        String container = "READY".equals(status) ? "mp4" : null;
        String videoCodec = "READY".equals(status) ? "h264" : null;
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO media
                        (media_uid, owner_id, source_upload_session_id, display_name,
                         original_name, size_bytes, duration_ms, container_format,
                         video_codec, width, height, status, version,
                         failure_code, failure_message, deleted_at)
                    VALUES
                        (?, ?, ?, ?, ?, 100, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, mediaUid);
            statement.setString(2, ownerId);
            statement.setLong(3, uploadId.longValue());
            statement.setString(4, displayName);
            statement.setString(5, displayName);
            if (durationMs == null) {
                statement.setNull(6, java.sql.Types.BIGINT);
            } else {
                statement.setLong(6, durationMs);
            }
            statement.setString(7, container);
            statement.setString(8, videoCodec);
            if (width == null) {
                statement.setNull(9, java.sql.Types.INTEGER);
            } else {
                statement.setInt(9, width);
            }
            if (height == null) {
                statement.setNull(10, java.sql.Types.INTEGER);
            } else {
                statement.setInt(10, height);
            }
            statement.setString(11, status);
            if (failureCode == null) {
                statement.setNull(12, java.sql.Types.VARCHAR);
                statement.setNull(13, java.sql.Types.VARCHAR);
            } else {
                statement.setString(12, failureCode);
                statement.setString(13, "素材处理失败");
            }
            if ("DELETED".equals(status)) {
                statement.setTimestamp(14, new java.sql.Timestamp(System.currentTimeMillis()));
            } else {
                statement.setNull(14, java.sql.Types.TIMESTAMP);
            }
            return statement;
        }, mediaKey);

        Number mediaId = mediaKey.getKey();
        assertNotNull(mediaId);

        if ("PROBE_PENDING".equals(status)) {
            jdbcTemplate.update(
                    """
                    INSERT INTO job
                        (job_uid, owner_id, media_id, job_type, status, stage,
                         input_media_version, max_attempts)
                    VALUES (?, ?, ?, 'MEDIA_INGEST', 'QUEUED', 'QUEUED', 1, 3)
                    """,
                    "job_test_" + UUID.randomUUID().toString().replace("-", ""),
                    ownerId,
                    mediaId.longValue());
        }

        return mediaUid;
    }
}