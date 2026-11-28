package com.vedioai.server.auth.service;

import com.vedioai.server.auth.dto.SessionItemView;
import com.vedioai.server.auth.security.SessionGuardService;
import com.vedioai.server.common.error.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;


@Service
public class SessionsService {

    private final SessionGuardService sessionGuardService;

    public SessionsService(SessionGuardService sessionGuardService) {
        this.sessionGuardService = sessionGuardService;
    }

    public List<SessionItemView> list(long userId, String currentSessionUid) {
        return sessionGuardService.listActive(userId).stream()
                .map(record -> new SessionItemView(
                        record.sessionUid(),
                        record.sessionUid().equals(currentSessionUid),
                        record.createdAt().toString(),
                        record.lastSeenAt().toString(),
                        record.absoluteExpiresAt().toString(),
                        record.userAgent(),
                        record.ipLabel()))
                .toList();
    }

    public boolean revoke(long userId, String sessionUid) {
        if (!sessionGuardService.existsActiveByUid(userId, sessionUid)) {
            throw new ResourceNotFoundException();
        }
        return sessionGuardService.revokeByUid(userId, sessionUid, "REVOKE");
    }
}
