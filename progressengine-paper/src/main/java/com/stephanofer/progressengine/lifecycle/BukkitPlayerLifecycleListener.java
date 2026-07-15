package com.stephanofer.progressengine.lifecycle;

import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;
import com.stephanofer.networkplayersettings.settings.event.PlayerSettingsReadyEvent;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class BukkitPlayerLifecycleListener implements Listener {
    private final PlayerLifecycleCoordinator coordinator;
    private final PlayerSettingsService playerSettings;

    public BukkitPlayerLifecycleListener(PlayerLifecycleCoordinator coordinator, PlayerSettingsService playerSettings) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.playerSettings = Objects.requireNonNull(playerSettings, "playerSettings");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        this.coordinator.preload(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSettingsReady(PlayerSettingsReadyEvent event) {
        Player player = event.player();
        if (!player.isOnline() || !this.playerSettings.isReady(player.getUniqueId())) {
            return;
        }
        this.coordinator.startSession(player.getUniqueId(), player.getName(), player::isOnline);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.coordinator.quit(event.getPlayer().getUniqueId());
    }
}
