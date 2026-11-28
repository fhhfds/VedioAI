package com.vedioai.server.common.identity;

import com.vedioai.server.common.error.ResourceNotFoundException;
import java.util.Objects;


/**
 * Shared ownership contract for future private media resources.
 * owner_id is derived from the authenticated CurrentActor, never from request data.
 */
public final class PrivateResourceAccess {

    private PrivateResourceAccess() {
    }

    public static String ownerId(CurrentActor actor) {
        return actor.ownerId();
    }

    public static void requireOwner(String ownerId, CurrentActor actor) {
        if (ownerId == null || !Objects.equals(ownerId, actor.ownerId())) {
            // 404 instead of 403 avoids leaking whether another user's resource exists.
            throw new ResourceNotFoundException();
        }
    }
}
