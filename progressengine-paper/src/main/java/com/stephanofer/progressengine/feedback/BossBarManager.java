package com.stephanofer.progressengine.feedback;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class BossBarManager implements AutoCloseable {
    private final JavaPlugin plugin;
    private final ConcurrentHashMap<Key, ActiveBossBar> active = new ConcurrentHashMap<>();

    public BossBarManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void show(Player player, String channel, BossBar bossBar, long durationTicks) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(bossBar, "bossBar");
        Key key = new Key(player.getUniqueId(), channel);
        ActiveBossBar previous = this.active.remove(key);
        if (previous != null) {
            previous.hide(player);
        }
        ActiveBossBar current = new ActiveBossBar(bossBar);
        this.active.put(key, current);
        player.showBossBar(bossBar);
        BukkitTask cleanup = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> expire(key, current), durationTicks);
        current.cleanup(cleanup);
    }

    public void clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Player player = this.plugin.getServer().getPlayer(playerId);
        this.active.entrySet().removeIf(entry -> {
            if (!entry.getKey().playerId().equals(playerId)) {
                return false;
            }
            entry.getValue().hide(player);
            return true;
        });
    }

    @Override
    public void close() {
        for (Map.Entry<Key, ActiveBossBar> entry : this.active.entrySet()) {
            Player player = this.plugin.getServer().getPlayer(entry.getKey().playerId());
            entry.getValue().hide(player);
        }
        this.active.clear();
    }

    private void expire(Key key, ActiveBossBar expected) {
        if (this.active.remove(key, expected)) {
            Player player = this.plugin.getServer().getPlayer(key.playerId());
            expected.hide(player);
        }
    }

    private record Key(UUID playerId, String channel) {
    }

    private static final class ActiveBossBar {
        private final BossBar bossBar;
        private volatile BukkitTask cleanup;

        private ActiveBossBar(BossBar bossBar) {
            this.bossBar = bossBar;
        }

        void cleanup(BukkitTask cleanup) {
            this.cleanup = cleanup;
        }

        void hide(Player player) {
            BukkitTask task = this.cleanup;
            if (task != null) {
                task.cancel();
            }
            if (player != null && player.isOnline()) {
                player.hideBossBar(this.bossBar);
            }
        }
    }
}
