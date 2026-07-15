package com.stephanofer.progressengine.identity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stephanofer.networkplayersettings.assets.api.CountryFlagService;
import com.stephanofer.networkplayersettings.settings.api.NickStyleRenderRequest;
import com.stephanofer.networkplayersettings.settings.api.PlayerStyleService;
import com.stephanofer.progressengine.config.ConfigurationSnapshot;
import com.stephanofer.progressengine.config.IdentitySettings.IdentityPart;
import com.stephanofer.progressengine.localization.LocalizedMessages;
import com.stephanofer.progressengine.localization.MessageArguments;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

public final class PlayerIdentityRenderer implements AutoCloseable {
    private final LuckPerms luckPerms;
    private final PlayerStyleService styles;
    private final CountryFlagService flags;
    private final Supplier<ConfigurationSnapshot> snapshotSupplier;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Cache<OfflineKey, CompletableFuture<Component>> offlineCache;

    public PlayerIdentityRenderer(LuckPerms luckPerms, PlayerStyleService styles, CountryFlagService flags,
                                  Supplier<ConfigurationSnapshot> snapshotSupplier, Logger logger) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
        this.styles = Objects.requireNonNull(styles, "styles");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
        ConfigurationSnapshot snapshot = snapshotSupplier.get();
        this.offlineCache = Caffeine.newBuilder()
            .maximumSize(snapshot.identity().offlineCacheMaximumSize())
            .expireAfterWrite(Duration.ofSeconds(snapshot.identity().offlineCacheExpireAfterWriteSeconds()))
            .build();
    }

    public Component renderOnline(Player player) {
        Objects.requireNonNull(player, "player");
        ConfigurationSnapshot snapshot = this.snapshotSupplier.get();
        List<Component> parts = new ArrayList<>();
        for (IdentityPart part : snapshot.identity().parts()) {
            Component component = switch (part) {
                case PREFIX -> renderPrefix(prefixOnline(player));
                case NICK -> this.styles.formattedNick(player);
                case COUNTRY_FLAG -> this.flags.flag(player.getUniqueId());
            };
            if (!isEmpty(component)) {
                parts.add(component);
            }
        }
        return join(parts, separator(snapshot));
    }

    public CompletableFuture<Component> renderOffline(UUID playerId, Optional<String> knownUsername) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(knownUsername, "knownUsername");
        ConfigurationSnapshot snapshot = this.snapshotSupplier.get();
        String username = knownUsername.filter(name -> !name.isBlank()).orElse(playerId.toString());
        OfflineKey key = new OfflineKey(playerId, username, snapshot.revision());
        return this.offlineCache.get(key, ignored -> loadOffline(snapshot, playerId, username));
    }

    public void invalidate(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        this.offlineCache.asMap().keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public void invalidateAll() {
        this.offlineCache.invalidateAll();
    }

    @Override
    public void close() {
        invalidateAll();
    }

    private CompletableFuture<Component> loadOffline(ConfigurationSnapshot snapshot, UUID playerId, String username) {
        CompletableFuture<User> userFuture = this.luckPerms.getUserManager().loadUser(playerId);
        CompletableFuture<Component> flagFuture = this.flags.flagAsync(playerId);
        return userFuture.thenCompose(user -> {
            Component prefix = renderPrefix(prefixOffline(user));
            CompletableFuture<Component> nickFuture = this.styles.formattedNick(new NickStyleRenderRequest(
                playerId,
                username,
                permission -> user.getCachedData().getPermissionData().checkPermission(permission).asBoolean()
            ));
            return nickFuture.thenCombine(flagFuture, (nick, flag) -> {
                List<Component> parts = new ArrayList<>();
                for (IdentityPart part : snapshot.identity().parts()) {
                    Component component = switch (part) {
                        case PREFIX -> prefix;
                        case NICK -> nick;
                        case COUNTRY_FLAG -> flag;
                    };
                    if (!isEmpty(component)) {
                        parts.add(component);
                    }
                }
                return join(parts, separator(snapshot));
            });
        }).whenComplete((component, failure) -> {
            if (failure != null) {
                invalidate(playerId);
            }
        });
    }

    private String prefixOnline(Player player) {
        return this.luckPerms.getPlayerAdapter(Player.class).getMetaData(player).getPrefix();
    }

    private static String prefixOffline(User user) {
        return user.getCachedData().getMetaData().getPrefix();
    }

    private Component renderPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Component.empty();
        }
        try {
            return this.miniMessage.deserialize(prefix);
        } catch (RuntimeException exception) {
            this.logger.log(Level.WARNING, "Invalid LuckPerms prefix MiniMessage; rendering it as plain text.", exception);
            return Component.text(prefix);
        }
    }

    private Component separator(ConfigurationSnapshot snapshot) {
        return new LocalizedMessages(() -> snapshot).render(snapshot.identity().separator(), MessageArguments.builder().build());
    }

    private static Component join(List<Component> parts, Component separator) {
        if (parts.isEmpty()) {
            return Component.empty();
        }
        Component result = parts.get(0);
        for (int index = 1; index < parts.size(); index++) {
            result = result.append(separator).append(parts.get(index));
        }
        return result;
    }

    private static boolean isEmpty(Component component) {
        return component == null || component.equals(Component.empty());
    }

    private record OfflineKey(UUID playerId, String username, long configRevision) {
    }
}
