package com.stephanofer.progressengine.identity;

import com.stephanofer.networkplayersettings.settings.api.SettingKey;
import com.stephanofer.networkplayersettings.settings.event.PlayerSettingChangeEvent;
import java.util.Objects;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class IdentityInvalidationListener implements Listener {
    private final PlayerIdentityRenderer renderer;

    public IdentityInvalidationListener(Plugin plugin, LuckPerms luckPerms, PlayerIdentityRenderer renderer) {
        Objects.requireNonNull(plugin, "plugin");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(luckPerms, "luckPerms").getEventBus().subscribe(
            plugin,
            UserDataRecalculateEvent.class,
            event -> this.renderer.invalidate(event.getUser().getUniqueId())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSettingChange(PlayerSettingChangeEvent event) {
        SettingKey key = event.settingKey();
        if (key == SettingKey.NICK_STYLE || key == SettingKey.SHOW_COUNTRY_FLAG || key == SettingKey.COUNTRY_OVERRIDE || key == SettingKey.DETECTED_COUNTRY) {
            this.renderer.invalidate(event.playerId());
        }
    }
}
