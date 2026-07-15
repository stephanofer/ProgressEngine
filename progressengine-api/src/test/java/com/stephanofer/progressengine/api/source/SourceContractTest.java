package com.stephanofer.progressengine.api.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SourceContractTest {
    @Test
    void playerActorRequiresOneValidPlayerId() {
        UUID playerId = UUID.randomUUID();

        assertEquals(playerId, OperationActor.player(playerId).playerId().orElseThrow());
        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationActor(ActorType.PLAYER, Optional.empty())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationActor(ActorType.PLUGIN, Optional.of(playerId))
        );
    }

    @Test
    void sourceTrimsAndRejectsBlankOrOversizedFields() {
        OperationSource source = new OperationSource(" HeraShop ", " lobby-1 ");

        assertEquals("HeraShop", source.pluginName());
        assertEquals("lobby-1", source.serverId());
        assertThrows(IllegalArgumentException.class, () -> new OperationSource(" ", "server"));
        assertThrows(IllegalArgumentException.class, () -> new OperationSource("plugin", "x".repeat(65)));
    }
}
