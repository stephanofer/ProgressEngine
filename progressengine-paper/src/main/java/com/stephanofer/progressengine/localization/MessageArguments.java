package com.stephanofer.progressengine.localization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;

public final class MessageArguments {
    private final Map<String, Component> components;
    private final Map<String, String> unparsed;

    private MessageArguments(Map<String, Component> components, Map<String, String> unparsed) {
        this.components = Map.copyOf(components);
        this.unparsed = Map.copyOf(unparsed);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Component> components() {
        return this.components;
    }

    public Map<String, String> unparsed() {
        return this.unparsed;
    }

    public static final class Builder {
        private final Map<String, Component> components = new LinkedHashMap<>();
        private final Map<String, String> unparsed = new LinkedHashMap<>();

        public Builder component(String key, Component value) {
            this.components.put(requireKey(key), Objects.requireNonNull(value, "value"));
            this.unparsed.remove(key);
            return this;
        }

        public Builder unparsed(String key, String value) {
            this.unparsed.put(requireKey(key), Objects.requireNonNull(value, "value"));
            this.components.remove(key);
            return this;
        }

        public MessageArguments build() {
            return new MessageArguments(this.components, this.unparsed);
        }

        private static String requireKey(String key) {
            Objects.requireNonNull(key, "key");
            if (!key.matches("[a-z0-9_]+")) {
                throw new IllegalArgumentException("message argument key must match [a-z0-9_]+");
            }
            return key;
        }
    }
}
