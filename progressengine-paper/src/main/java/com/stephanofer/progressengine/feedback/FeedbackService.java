package com.stephanofer.progressengine.feedback;

import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;
import com.stephanofer.progressengine.config.ConfigurationSnapshot;
import com.stephanofer.progressengine.config.FeedbackActionConfig;
import com.stephanofer.progressengine.localization.LocalizedMessages;
import com.stephanofer.progressengine.localization.MessageArguments;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class FeedbackService implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final PlayerSettingsService playerSettings;
    private final LocalizedMessages messages;
    private final BossBarManager bossBars;
    private final Supplier<ConfigurationSnapshot> snapshotSupplier;
    private final Logger logger;

    public FeedbackService(JavaPlugin plugin, PlayerSettingsService playerSettings, LocalizedMessages messages,
                           BossBarManager bossBars, Supplier<ConfigurationSnapshot> snapshotSupplier, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerSettings = Objects.requireNonNull(playerSettings, "playerSettings");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.bossBars = Objects.requireNonNull(bossBars, "bossBars");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void sendAward(UUID playerId, long amount, long balance) {
        sendPlayer(playerId, "award-received", language -> awardArguments(amount, balance, language));
    }

    public void sendTransferReceived(UUID playerId, Component sender, long amount, long balance) {
        Objects.requireNonNull(sender, "sender");
        sendPlayer(playerId, "transfer-received", language -> transferArguments(sender, amount, balance, language));
    }

    public void sendPlayer(UUID playerId, String feedbackKey, ArgumentFactory arguments) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(feedbackKey, "feedbackKey");
        Objects.requireNonNull(arguments, "arguments");
        Runnable task = () -> {
            Player player = this.plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline() || !this.playerSettings.isReady(playerId)) {
                return;
            }
            String language = this.playerSettings.resolvedLanguage(player).code();
            dispatch(player, feedbackKey, language, arguments.create(language));
        };
        runOnMain(task);
    }

    public void send(CommandSender sender, String messageKey, MessageArguments arguments) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(messageKey, "messageKey");
        String language = sender instanceof Player player
            ? this.playerSettings.resolvedLanguage(player).code()
            : activeConsoleLanguage();
        sender.sendMessage(this.messages.message(messageKey, language, arguments));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.bossBars.clear(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        this.bossBars.close();
    }

    private void dispatch(Audience audience, String feedbackKey, String language, MessageArguments arguments) {
        List<FeedbackActionConfig> actions = this.messages.feedback(feedbackKey, language);
        for (FeedbackActionConfig action : actions) {
            try {
                dispatchAction(audience, language, arguments, action);
            } catch (RuntimeException exception) {
                this.logger.log(Level.WARNING, "ProgressEngine feedback action failed for " + feedbackKey, exception);
            }
        }
    }

    private void dispatchAction(Audience audience, String language, MessageArguments arguments, FeedbackActionConfig action) {
        if (action instanceof FeedbackActionConfig.Chat chat) {
            audience.sendMessage(render(chat.message(), arguments));
            return;
        }
        if (!(audience instanceof Player player)) {
            return;
        }
        if (action instanceof FeedbackActionConfig.ActionBar actionBar) {
            player.sendActionBar(render(actionBar.message(), arguments));
            return;
        }
        if (action instanceof FeedbackActionConfig.Title title) {
            player.showTitle(Title.title(
                render(title.title(), arguments),
                title.subtitle().isBlank() ? Component.empty() : render(title.subtitle(), arguments),
                Title.Times.times(ticks(title.fadeInTicks()), ticks(title.stayTicks()), ticks(title.fadeOutTicks()))
            ));
            return;
        }
        if (action instanceof FeedbackActionConfig.Sound sound) {
            player.playSound(Sound.sound(Key.key(sound.sound()), source(sound.source()), sound.volume(), sound.pitch()));
            return;
        }
        if (action instanceof FeedbackActionConfig.BossBar bossBar) {
            BossBar bar = BossBar.bossBar(
                render(bossBar.message(), arguments),
                bossBar.progress(),
                bossBarColor(bossBar.color()),
                bossBarOverlay(bossBar.overlay())
            );
            this.bossBars.show(player, bossBar.channel(), bar, bossBar.durationTicks());
        }
    }

    private Component render(String template, MessageArguments arguments) {
        return this.messages.render(template, arguments);
    }

    private MessageArguments awardArguments(long amount, long balance, String language) {
        return MessageArguments.builder()
            .unparsed("amount", this.messages.formatted(amount, language))
            .unparsed("amount_raw", this.messages.raw(amount))
            .unparsed("amount_compact", this.messages.compact(amount, language))
            .unparsed("balance", this.messages.formatted(balance, language))
            .unparsed("balance_raw", this.messages.raw(balance))
            .unparsed("balance_compact", this.messages.compact(balance, language))
            .build();
    }

    private MessageArguments transferArguments(Component sender, long amount, long balance, String language) {
        return MessageArguments.builder()
            .component("sender", sender)
            .unparsed("amount", this.messages.formatted(amount, language))
            .unparsed("amount_raw", this.messages.raw(amount))
            .unparsed("amount_compact", this.messages.compact(amount, language))
            .unparsed("balance", this.messages.formatted(balance, language))
            .unparsed("balance_raw", this.messages.raw(balance))
            .unparsed("balance_compact", this.messages.compact(balance, language))
            .build();
    }

    private String activeConsoleLanguage() {
        return this.snapshotSupplier.get().localization().consoleLanguage();
    }

    private void runOnMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        try {
            this.plugin.getServer().getScheduler().runTask(this.plugin, task);
        } catch (RuntimeException exception) {
            this.logger.log(Level.WARNING, "ProgressEngine could not schedule feedback during shutdown", exception);
        }
    }

    private static Duration ticks(long ticks) {
        return Duration.ofMillis(Math.multiplyExact(ticks, 50L));
    }

    private static Sound.Source source(String source) {
        return Sound.Source.valueOf(source.toUpperCase(Locale.ROOT));
    }

    private static BossBar.Color bossBarColor(String color) {
        return BossBar.Color.valueOf(color.toUpperCase(Locale.ROOT));
    }

    private static BossBar.Overlay bossBarOverlay(String overlay) {
        return BossBar.Overlay.valueOf(overlay.toUpperCase(Locale.ROOT));
    }

    @FunctionalInterface
    public interface ArgumentFactory {
        MessageArguments create(String language);
    }
}
