package com.stephanofer.progressengine.config;

import com.stephanofer.progressengine.api.operation.OperationReason;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record CommandSettings(Registration registration, Availability availability, Permissions permissions,
                              Pay pay, History history, Suggestions suggestions, Reasons reasons) {
    public static final int CURRENT_VERSION = 1;
    private static final Pattern COMMAND_LABEL = Pattern.compile("[a-z][a-z0-9_-]{0,31}");
    private static final Pattern PERMISSION = Pattern.compile("[a-z0-9._-]+(\\.[a-z0-9._-]+)*");

    public CommandSettings {
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(pay, "pay");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(suggestions, "suggestions");
        Objects.requireNonNull(reasons, "reasons");
    }

    public static CommandSettings defaults() {
        return new CommandSettings(
            new Registration("points", List.of()),
            Availability.enabled(),
            Permissions.defaults(),
            new Pay(1L, 1_000_000_000L, 5L, new Confirmation(true, 100_000L, 30L), 86_400L),
            new History(10, 120L, ZoneId.of("UTC")),
            new Suggestions(10_000, 300L),
            Reasons.defaults()
        );
    }

    public record Registration(String root, List<String> aliases) {
        public Registration {
            root = requireCommandLabel(root, "root");
            Objects.requireNonNull(aliases, "aliases");
            aliases = List.copyOf(aliases.stream().map(alias -> requireCommandLabel(alias, "alias")).toList());
            if (Set.copyOf(aliases).size() != aliases.size()) {
                throw new IllegalArgumentException("aliases cannot contain duplicates");
            }
            if (aliases.contains(root)) {
                throw new IllegalArgumentException("aliases cannot contain the root command");
            }
        }
    }

    public record Availability(Map<CommandFeature, Boolean> features) {
        public Availability {
            Objects.requireNonNull(features, "features");
            java.util.EnumMap<CommandFeature, Boolean> copy = new java.util.EnumMap<>(CommandFeature.class);
            for (CommandFeature feature : CommandFeature.values()) {
                copy.put(feature, features.getOrDefault(feature, true));
            }
            features = Map.copyOf(copy);
        }

        public boolean enabled(CommandFeature feature) {
            return this.features.getOrDefault(feature, true);
        }

        public static Availability enabled() {
            java.util.EnumMap<CommandFeature, Boolean> map = new java.util.EnumMap<>(CommandFeature.class);
            for (CommandFeature feature : CommandFeature.values()) {
                map.put(feature, true);
            }
            return new Availability(map);
        }
    }

    public record Permissions(Map<CommandPermission, String> values) {
        public Permissions {
            Objects.requireNonNull(values, "values");
            java.util.EnumMap<CommandPermission, String> copy = new java.util.EnumMap<>(CommandPermission.class);
            Map<CommandPermission, String> defaults = defaultValues();
            for (CommandPermission permission : CommandPermission.values()) {
                copy.put(permission, requirePermission(values.getOrDefault(permission, defaults.get(permission)), permission.configKey()));
            }
            values = Map.copyOf(copy);
        }

        public String require(CommandPermission permission) {
            return this.values.get(permission);
        }

        public static Permissions defaults() {
            return new Permissions(defaultValues());
        }

        private static Map<CommandPermission, String> defaultValues() {
            return Map.ofEntries(
                Map.entry(CommandPermission.BALANCE, "progressengine.command.balance"),
                Map.entry(CommandPermission.BALANCE_OTHERS, "progressengine.command.balance.others"),
                Map.entry(CommandPermission.PAY, "progressengine.command.pay"),
                Map.entry(CommandPermission.HISTORY, "progressengine.command.history"),
                Map.entry(CommandPermission.HELP, "progressengine.command.help"),
                Map.entry(CommandPermission.ADMIN_ADD, "progressengine.command.admin.add"),
                Map.entry(CommandPermission.ADMIN_REMOVE, "progressengine.command.admin.remove"),
                Map.entry(CommandPermission.ADMIN_SET, "progressengine.command.admin.set"),
                Map.entry(CommandPermission.ADMIN_RESET, "progressengine.command.admin.reset"),
                Map.entry(CommandPermission.ADMIN_HISTORY, "progressengine.command.admin.history"),
                Map.entry(CommandPermission.ADMIN_RELOAD, "progressengine.command.admin.reload"),
                Map.entry(CommandPermission.ADMIN_STATUS, "progressengine.command.admin.status")
            );
        }
    }

    public record Pay(long minimum, long maximum, long cooldownSeconds, Confirmation confirmation,
                      long retryRetentionSeconds) {
        public Pay {
            if (minimum < 1L) throw new IllegalArgumentException("pay minimum must be positive");
            if (maximum < minimum) throw new IllegalArgumentException("pay maximum must be greater than or equal to minimum");
            if (cooldownSeconds < 0L || cooldownSeconds > 86_400L) {
                throw new IllegalArgumentException("pay cooldown must be between 0 and 86400 seconds");
            }
            Objects.requireNonNull(confirmation, "confirmation");
            if (confirmation.threshold() < minimum || confirmation.threshold() > maximum) {
                throw new IllegalArgumentException("pay confirmation threshold must be inside the pay range");
            }
            if (retryRetentionSeconds < 60L || retryRetentionSeconds > 604_800L) {
                throw new IllegalArgumentException("pay retry retention must be between 60 and 604800 seconds");
            }
        }
    }

    public record Confirmation(boolean enabled, long threshold, long expirySeconds) {
        public Confirmation {
            if (threshold < 1L) throw new IllegalArgumentException("confirmation threshold must be positive");
            if (expirySeconds < 5L || expirySeconds > 3_600L) {
                throw new IllegalArgumentException("confirmation expiry must be between 5 and 3600 seconds");
            }
        }
    }

    public record History(int pageSize, long sessionExpirySeconds, ZoneId timeZone) {
        public History {
            if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("history page size must be between 1 and 100");
            if (sessionExpirySeconds < 10L || sessionExpirySeconds > 3_600L) {
                throw new IllegalArgumentException("history session expiry must be between 10 and 3600 seconds");
            }
            Objects.requireNonNull(timeZone, "timeZone");
        }
    }

    public record Suggestions(int maximumSize, long refreshSeconds) {
        public Suggestions {
            if (maximumSize < 1 || maximumSize > 10_000) {
                throw new IllegalArgumentException("suggestions maximum size must be between 1 and 10000");
            }
            if (refreshSeconds < 30L || refreshSeconds > 86_400L) {
                throw new IllegalArgumentException("suggestions refresh must be between 30 and 86400 seconds");
            }
        }
    }

    public record Reasons(OperationReason playerTransfer, OperationReason adminAdd, OperationReason adminRemove,
                          OperationReason adminSet, OperationReason adminReset) {
        public Reasons {
            Objects.requireNonNull(playerTransfer, "playerTransfer");
            Objects.requireNonNull(adminAdd, "adminAdd");
            Objects.requireNonNull(adminRemove, "adminRemove");
            Objects.requireNonNull(adminSet, "adminSet");
            Objects.requireNonNull(adminReset, "adminReset");
        }

        public static Reasons defaults() {
            return new Reasons(
                OperationReason.of("progressengine:player_transfer"),
                OperationReason.of("progressengine:admin_add"),
                OperationReason.of("progressengine:admin_remove"),
                OperationReason.of("progressengine:admin_set"),
                OperationReason.of("progressengine:admin_reset")
            );
        }
    }

    public enum CommandFeature {
        BALANCE("balance"),
        PAY("pay"),
        HISTORY("history"),
        HELP("help"),
        ADMIN_ADD("admin-add"),
        ADMIN_REMOVE("admin-remove"),
        ADMIN_SET("admin-set"),
        ADMIN_RESET("admin-reset"),
        ADMIN_HISTORY("admin-history"),
        ADMIN_RELOAD("admin-reload"),
        ADMIN_STATUS("admin-status");

        private final String configKey;

        CommandFeature(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return this.configKey;
        }

        public static CommandFeature fromConfigKey(String key) {
            for (CommandFeature feature : values()) {
                if (feature.configKey.equals(key)) return feature;
            }
            throw new IllegalArgumentException("unknown command feature: " + key);
        }
    }

    public enum CommandPermission {
        BALANCE("balance"),
        BALANCE_OTHERS("balance-others"),
        PAY("pay"),
        HISTORY("history"),
        HELP("help"),
        ADMIN_ADD("admin-add"),
        ADMIN_REMOVE("admin-remove"),
        ADMIN_SET("admin-set"),
        ADMIN_RESET("admin-reset"),
        ADMIN_HISTORY("admin-history"),
        ADMIN_RELOAD("admin-reload"),
        ADMIN_STATUS("admin-status");

        private final String configKey;

        CommandPermission(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return this.configKey;
        }

        public static CommandPermission fromConfigKey(String key) {
            for (CommandPermission permission : values()) {
                if (permission.configKey.equals(key)) return permission;
            }
            throw new IllegalArgumentException("unknown command permission: " + key);
        }
    }

    private static String requireCommandLabel(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!COMMAND_LABEL.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must match [a-z][a-z0-9_-]{0,31}");
        }
        return value;
    }

    private static String requirePermission(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!PERMISSION.matcher(value).matches() || value.length() > 128) {
            throw new IllegalArgumentException(label + " permission is invalid");
        }
        return value;
    }
}
