package com.vedioai.server.identity.security;

import java.util.Set;

public record CurrentActor(long userId, String userUid, Set<String> roles) {

    public CurrentActor {
        roles = Set.copyOf(roles);
    }
}