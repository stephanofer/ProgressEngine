package com.stephanofer.progressengine.command;

import com.stephanofer.progressengine.persistence.KnownPlayerName;
import com.stephanofer.progressengine.persistence.ProgressPersistence;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Server;
import org.bukkit.entity.Player;

final class CommandTargetResolver {
    private final Server server;
    private final ProgressPersistence persistence;
    private final KnownPlayerSuggestionIndex suggestions;

    CommandTargetResolver(Server server, ProgressPersistence persistence, KnownPlayerSuggestionIndex suggestions) {
        this.server = Objects.requireNonNull(server, "server");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.suggestions = Objects.requireNonNull(suggestions, "suggestions");
    }

    CompletableFuture<Optional<ResolvedTarget>> playerTarget(String input) {
        Player online = this.server.getPlayerExact(input);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new ResolvedTarget(online.getUniqueId(), Optional.of(online.getName()))));
        }
        Optional<KnownPlayerName> cached = this.suggestions.known(input);
        if (cached.isPresent()) {
            KnownPlayerName name = cached.orElseThrow();
            return CompletableFuture.completedFuture(Optional.of(new ResolvedTarget(name.playerId(), Optional.of(name.username()))));
        }
        return this.persistence.playerNames().findByUsername(input)
            .thenApply(found -> found.map(name -> new ResolvedTarget(name.playerId(), Optional.of(name.username()))));
    }

    CompletableFuture<Optional<ResolvedTarget>> administrativeTarget(String input) {
        Optional<UUID> uuid = CommandParsers.uuid(input);
        if (uuid.isPresent()) {
            return this.persistence.playerNames().findByPlayerId(uuid.orElseThrow())
                .thenApply(name -> Optional.of(new ResolvedTarget(uuid.orElseThrow(), name.map(KnownPlayerName::username))));
        }
        return playerTarget(input);
    }
}
