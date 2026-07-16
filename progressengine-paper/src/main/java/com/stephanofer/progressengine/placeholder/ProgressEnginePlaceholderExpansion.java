package com.stephanofer.progressengine.placeholder;

import com.stephanofer.networkplayersettings.settings.api.SettingKey;
import com.stephanofer.networkplayersettings.settings.event.PlayerSettingChangeEvent;
import com.stephanofer.networkplayersettings.settings.event.PlayerSettingsReadyEvent;
import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.config.ConfigurationSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ProgressEnginePlaceholderExpansion extends PlaceholderExpansion implements Listener, AutoCloseable {
    private static final String IDENTIFIER = "progressengine";

    private final JavaPlugin plugin;
    private final PlaceholderResolver resolver;
    private final ConcurrentMap<UUID, String> languages = new ConcurrentHashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(true);

    public ProgressEnginePlaceholderExpansion(JavaPlugin plugin, Predicate<UUID> readyLookup,
                                              Function<UUID, Optional<BalanceSnapshot>> snapshotLookup,
                                              Supplier<ConfigurationSnapshot> snapshotSupplier) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.resolver = new PlaceholderResolver(readyLookup, snapshotLookup, this::language, snapshotSupplier);
    }

    public static String identifier() {
        return IDENTIFIER;
    }

    public @Nullable String language(UUID playerId) {
        return this.languages.get(playerId);
    }

    public void rememberLanguage(UUID playerId, String language) {
        Objects.requireNonNull(playerId, "playerId");
        if (!this.active.get() || language == null || language.isBlank()) {
            return;
        }
        this.languages.put(playerId, language);
    }

    @Override
    public @NotNull String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public @NotNull String getAuthor() {
        List<String> authors = this.plugin.getPluginMeta().getAuthors();
        if (authors.isEmpty()) {
            return this.plugin.getName();
        }
        return String.join(", ", authors);
    }

    @Override
    public @NotNull String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull List<String> getPlaceholders() {
        return List.of(
            PlaceholderResolver.POINTS,
            PlaceholderResolver.POINTS_FORMATTED,
            PlaceholderResolver.POINTS_COMPACT,
            PlaceholderResolver.READY
        );
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        UUID playerId = player == null ? null : player.getUniqueId();
        return this.resolver.resolve(playerId, params, this.active.get());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSettingsReady(PlayerSettingsReadyEvent event) {
        rememberLanguage(event.player().getUniqueId(), event.resolvedLanguage().code());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSettingChange(PlayerSettingChangeEvent event) {
        if (event.settingKey() != SettingKey.LANGUAGE) {
            return;
        }
        rememberLanguage(event.playerId(), event.newResolvedValue());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.languages.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        if (!this.active.compareAndSet(true, false)) {
            return;
        }
        this.languages.clear();
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            unregister();
        } catch (RuntimeException exception) {
            this.plugin.getLogger().log(Level.WARNING, "ProgressEngine could not unregister PlaceholderAPI expansion", exception);
        }
    }
}
