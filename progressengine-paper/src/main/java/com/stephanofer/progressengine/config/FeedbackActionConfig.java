package com.stephanofer.progressengine.config;

import java.util.Objects;

public sealed interface FeedbackActionConfig permits FeedbackActionConfig.Chat, FeedbackActionConfig.ActionBar,
    FeedbackActionConfig.Title, FeedbackActionConfig.Sound, FeedbackActionConfig.BossBar {

    record Chat(String message) implements FeedbackActionConfig {
        public Chat {
            message = requireTemplate(message, "message");
        }
    }

    record ActionBar(String message) implements FeedbackActionConfig {
        public ActionBar {
            message = requireTemplate(message, "message");
        }
    }

    record Title(String title, String subtitle, long fadeInTicks, long stayTicks, long fadeOutTicks) implements FeedbackActionConfig {
        public Title {
            title = requireTemplate(title, "title");
            subtitle = Objects.requireNonNullElse(subtitle, "");
            requireTicks(fadeInTicks, "fadeInTicks", 0L, 200L);
            requireTicks(stayTicks, "stayTicks", 1L, 1_200L);
            requireTicks(fadeOutTicks, "fadeOutTicks", 0L, 200L);
        }
    }

    record Sound(String sound, String source, float volume, float pitch) implements FeedbackActionConfig {
        public Sound {
            sound = requireNamespaced(sound, "sound");
            source = Objects.requireNonNull(source, "source").trim().toLowerCase(java.util.Locale.ROOT);
            if (source.isBlank()) {
                throw new IllegalArgumentException("source cannot be blank");
            }
            if (!Float.isFinite(volume) || volume < 0.0F || volume > 4.0F) {
                throw new IllegalArgumentException("volume must be finite and between 0.0 and 4.0");
            }
            if (!Float.isFinite(pitch) || pitch < 0.5F || pitch > 2.0F) {
                throw new IllegalArgumentException("pitch must be finite and between 0.5 and 2.0");
            }
        }
    }

    record BossBar(String channel, String message, String color, String overlay, float progress, long durationTicks) implements FeedbackActionConfig {
        public BossBar {
            channel = requireNamespaced(channel, "channel");
            message = requireTemplate(message, "message");
            color = requireToken(color, "color");
            overlay = requireToken(overlay, "overlay");
            if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
                throw new IllegalArgumentException("progress must be finite and between 0.0 and 1.0");
            }
            requireTicks(durationTicks, "durationTicks", 1L, 1_200L);
        }
    }

    static String requireTemplate(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 1_024) {
            throw new IllegalArgumentException(name + " must be present and at most 1024 characters");
        }
        return value;
    }

    static String requireNamespaced(String value, String name) {
        String token = requireToken(value, name);
        if (!token.matches("[a-z0-9._-]+:[a-z0-9/._-]+") || token.length() > 128) {
            throw new IllegalArgumentException(name + " must be a valid namespaced key");
        }
        return token;
    }

    static String requireToken(String value, String name) {
        Objects.requireNonNull(value, name);
        String token = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (token.isBlank() || token.length() > 64) {
            throw new IllegalArgumentException(name + " must be present and at most 64 characters");
        }
        return token;
    }

    private static void requireTicks(long value, String name, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }
}
