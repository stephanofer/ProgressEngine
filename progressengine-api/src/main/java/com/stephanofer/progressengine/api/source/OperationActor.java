package com.stephanofer.progressengine.api.source;

import com.stephanofer.progressengine.api.internal.ApiValidation;
import java.util.Optional;
import java.util.UUID;

/**
 * Actor that initiated or authorized an operation.
 */
public record OperationActor(ActorType type, Optional<UUID> playerId) {

    /**
     * Creates an actor.
     *
     * @param type actor type
     * @param playerId player UUID when the actor type is {@link ActorType#PLAYER}
     */
    public OperationActor {
        if (type == null) {
            throw new NullPointerException("type cannot be null");
        }
        if (playerId == null) {
            throw new NullPointerException("playerId cannot be null");
        }
        if (type == ActorType.PLAYER) {
            UUID value = playerId.orElseThrow(() -> new IllegalArgumentException("player actor requires a playerId"));
            playerId = Optional.of(ApiValidation.requireUuid(value, "playerId"));
        } else if (playerId.isPresent()) {
            throw new IllegalArgumentException(type + " actor cannot include a playerId");
        }
    }

    /**
     * Creates a system actor.
     *
     * @return system actor
     */
    public static OperationActor system() {
        return new OperationActor(ActorType.SYSTEM, Optional.empty());
    }

    /**
     * Creates a plugin actor.
     *
     * @return plugin actor
     */
    public static OperationActor plugin() {
        return new OperationActor(ActorType.PLUGIN, Optional.empty());
    }

    /**
     * Creates a console actor.
     *
     * @return console actor
     */
    public static OperationActor console() {
        return new OperationActor(ActorType.CONSOLE, Optional.empty());
    }

    /**
     * Creates a player actor.
     *
     * @param playerId the player actor UUID
     * @return player actor
     */
    public static OperationActor player(UUID playerId) {
        return new OperationActor(ActorType.PLAYER, Optional.of(ApiValidation.requireUuid(playerId, "playerId")));
    }
}
