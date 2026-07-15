package com.stephanofer.progressengine.command;

import com.stephanofer.progressengine.config.CommandSettings;
import com.stephanofer.progressengine.persistence.KnownPlayerName;
import com.stephanofer.progressengine.persistence.ProgressPersistence;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

final class KnownPlayerSuggestionIndex implements AutoCloseable {
    private final JavaPlugin plugin;
    private final ProgressPersistence persistence;
    private final Supplier<CommandSettings> settings;
    private final Logger logger;
    private final AtomicReference<List<KnownPlayerName>> snapshot = new AtomicReference<>(List.of());
    private final Map<String, KnownPlayerName> byNormalized = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private org.bukkit.scheduler.BukkitTask task;

    KnownPlayerSuggestionIndex(JavaPlugin plugin, ProgressPersistence persistence, Supplier<CommandSettings> settings, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void start() {
        refresh();
        long ticks = Math.max(20L, this.settings.get().suggestions().refreshSeconds() * 20L);
        this.task = this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(this.plugin, this::refresh, ticks, ticks);
    }

    CompletableFuture<Void> refresh() {
        if (this.closed.get()) return CompletableFuture.completedFuture(null);
        int limit = this.settings.get().suggestions().maximumSize();
        return this.persistence.playerNames().loadRecentSuggestions(limit).thenAccept(names -> {
            if (this.closed.get()) return;
            this.snapshot.set(List.copyOf(names));
            this.byNormalized.clear();
            for (KnownPlayerName name : names) {
                this.byNormalized.put(name.normalizedUsername(), name);
            }
        }).exceptionally(failure -> {
            this.logger.log(Level.WARNING, "ProgressEngine failed to refresh command suggestions", failure);
            return null;
        });
    }

    Optional<KnownPlayerName> known(String username) {
        if (username == null) return Optional.empty();
        return Optional.ofNullable(this.byNormalized.get(username.toLowerCase(Locale.ROOT)));
    }

    List<String> suggestions(String prefix) {
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (KnownPlayerName name : this.snapshot.get()) {
            if (name.normalizedUsername().startsWith(normalizedPrefix)) suggestions.add(name.username());
            if (suggestions.size() >= 100) break;
        }
        return suggestions;
    }

    void updateKnown(UUID playerId, String username) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(username, "username");
        refresh();
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) return;
        if (this.task != null) this.task.cancel();
        this.snapshot.set(List.of());
        this.byNormalized.clear();
    }
}
