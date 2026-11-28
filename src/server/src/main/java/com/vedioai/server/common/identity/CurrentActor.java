package com.vedioai.server.common.identity;

import java.io.Serializable;
import java.util.Set;


public record CurrentActor(
        long userId,
        String userUid,
        Set<String> roles,
        String sessionUid,
        long authVersion) implements Serializable {

    private static final long serialVersionUID = 1L;

    public CurrentActor {
        roles = Set.copyOf(roles);
    }

    /**
     * Trusted owner identifier for private resources such as media.
     * Never take an owner id from a client request.
     */
    public String ownerId() {
        return userUid;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public String toString() {
        // Spring Session stores Authentication.getName() in PRINCIPAL_NAME (100 chars).
        return userUid;
    }
}
