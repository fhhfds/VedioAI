package com.vedioai.server.common.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vedioai.server.common.error.ResourceNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;


class CurrentActorTest {

    @Test
    void ownerIdComesFromTheAuthenticatedActorOnly() throws Exception {
        CurrentActor actor = new CurrentActor(
                7L,
                "usr_owner",
                Set.of("USER", "ADMIN"),
                "ses_public",
                3L);

        assertEquals("usr_owner", actor.ownerId());
        assertEquals("usr_owner", PrivateResourceAccess.ownerId(actor));
        PrivateResourceAccess.requireOwner("usr_owner", actor);
        assertThrows(ResourceNotFoundException.class,
                () -> PrivateResourceAccess.requireOwner("usr_other", actor));
        assertTrue(actor.hasRole("ADMIN"));
        assertEquals("usr_owner", actor.toString());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(actor);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            CurrentActor restored = (CurrentActor) input.readObject();
            assertEquals(actor, restored);
        }
    }
}
