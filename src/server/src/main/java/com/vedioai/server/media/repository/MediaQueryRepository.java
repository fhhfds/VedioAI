package com.vedioai.server.media.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.vedioai.server.media.domain.MediaStatus;
import com.vedioai.server.media.dto.DeletionImpactView;
import com.vedioai.server.media.dto.MediaDetailView;
import com.vedioai.server.media.dto.MediaFailureView;
import com.vedioai.server.media.dto.MediaSummaryView;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MediaQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public MediaQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page list(
            String ownerId,
            String query,
            String status,
            int page,
            int pageSize,
            String sort) {

        List<Object> arguments = new ArrayList<>();
        StringBuilder where = new StringBuilder(
                " WHERE m.owner_id = ? AND m.status <> 'DELETED' AND m.deleted_at IS NULL");
        arguments.add(ownerId);

        if (query != null) {
            where.append(" AND m.display_name LIKE ? ESCAPE '!'");
            arguments.add("%" + escapeLike(query) + "%");
        }
        if (status != null) {
            where.append(" AND m.status = ?");
            arguments.add(status);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM media m" + where,
                Long.class,
                arguments.toArray());

        String orderBy = "NAME_ASC".equals(sort)
                ? " ORDER BY m.display_name ASC, m.media_uid ASC"
                : " ORDER BY m.created_at DESC, m.media_uid DESC";

        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(pageSize);
        pageArguments.add((page - 1) * pageSize);

        List<MediaSummaryView> items = jdbcTemplate.query(
                """
                SELECT m.media_uid, m.display_name, m.size_bytes, m.duration_ms,
                       m.status, m.version, m.created_at
                FROM media m
                """ + where + orderBy + " LIMIT ? OFFSET ?",
                (rs, rowNum) -> summary(rs),
                pageArguments.toArray());

        return new Page(items, total == null ? 0 : total);
    }

    public Optional<MediaDetailView> find(String ownerId, String mediaId) {
        List<MediaDetailView> rows = jdbcTemplate.query(
                """
                SELECT m.media_uid, m.display_name, m.original_name, m.size_bytes,
                       m.duration_ms, m.container_format, m.video_codec, m.audio_codec,
                       m.width, m.height, m.status, m.version,
                       m.failure_code, m.failure_message, m.created_at,
                       j.job_uid
                FROM media m
                LEFT JOIN job j ON j.media_id = m.id AND j.job_type = 'MEDIA_INGEST'
                WHERE m.media_uid = ?
                  AND m.owner_id = ?
                  AND m.status <> 'DELETED'
                  AND m.deleted_at IS NULL
                """,
                (rs, rowNum) -> detail(rs),
                mediaId,
                ownerId);
        return rows.stream().findFirst();
    }

    private MediaSummaryView summary(ResultSet rs) throws SQLException {
        MediaStatus status = MediaStatus.valueOf(rs.getString("status"));
        return new MediaSummaryView(
                rs.getString("media_uid"),
                rs.getString("display_name"),
                rs.getLong("size_bytes"),
                nullableLong(rs, "duration_ms"),
                status.name(),
                rs.getLong("version"),
                instant(rs.getTimestamp("created_at")),
                allowedActions(status));
    }

    private MediaDetailView detail(ResultSet rs) throws SQLException {
        MediaStatus status = MediaStatus.valueOf(rs.getString("status"));
        String failureCode = rs.getString("failure_code");
        String failureMessage = rs.getString("failure_message");
        MediaFailureView failure = failureCode == null
                ? null
                : new MediaFailureView(failureCode, failureMessage);

        return new MediaDetailView(
                rs.getString("media_uid"),
                rs.getString("display_name"),
                rs.getLong("size_bytes"),
                nullableLong(rs, "duration_ms"),
                status.name(),
                rs.getLong("version"),
                instant(rs.getTimestamp("created_at")),
                allowedActions(status),
                rs.getString("original_name"),
                rs.getString("container_format"),
                rs.getString("video_codec"),
                rs.getString("audio_codec"),
                nullableInteger(rs, "width"),
                nullableInteger(rs, "height"),
                failure,
                rs.getString("job_uid"),
                new DeletionImpactView(
                        status.isDeletable(),
                        status.isDeletable() ? List.of() : List.of("PROCESSING")));
    }

    private static List<String> allowedActions(MediaStatus status) {
        List<String> actions = new ArrayList<>();
        actions.add("READ");
        if (status.isPlayable()) {
            actions.add("PLAY");
        }
        if (status.isDeletable()) {
            actions.add("DELETE");
        }
        return List.copyOf(actions);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static String instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    public record Page(List<MediaSummaryView> items, long total) {
    }
}