package com.stephanofer.progressengine.command;

import com.stephanofer.progressengine.feedback.FeedbackService;
import com.stephanofer.progressengine.localization.MessageArguments;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class CommandResponder {
    private final JavaPlugin plugin;
    private final FeedbackService feedback;
    private final Logger logger;

    CommandResponder(JavaPlugin plugin, FeedbackService feedback, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void send(CommandSender sender, String key, MessageArguments arguments) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(arguments, "arguments");
        Runnable task = () -> {
            if (sender instanceof Player player && !player.isOnline()) {
                return;
            }
            this.feedback.send(sender, key, arguments);
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        try {
            this.plugin.getServer().getScheduler().runTask(this.plugin, task);
        } catch (RuntimeException exception) {
            this.logger.log(Level.WARNING, "ProgressEngine could not schedule command feedback", exception);
        }
    }

    void send(CommandSender sender, String key) {
        send(sender, key, MessageArguments.builder().build());
    }
}
