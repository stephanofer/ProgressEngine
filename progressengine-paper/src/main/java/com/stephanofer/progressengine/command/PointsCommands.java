package com.stephanofer.progressengine.command;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.stephanofer.progressengine.account.BalanceStore;
import com.stephanofer.progressengine.api.PointsClient;
import com.stephanofer.progressengine.api.account.BalanceSnapshot;
import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationMetadata;
import com.stephanofer.progressengine.api.operation.OperationReason;
import com.stephanofer.progressengine.api.request.CreditRequest;
import com.stephanofer.progressengine.api.request.DebitRequest;
import com.stephanofer.progressengine.api.request.ResetBalanceRequest;
import com.stephanofer.progressengine.api.request.SetBalanceRequest;
import com.stephanofer.progressengine.api.request.TransferRequest;
import com.stephanofer.progressengine.api.result.CreditResult;
import com.stephanofer.progressengine.api.result.DebitResult;
import com.stephanofer.progressengine.api.result.ResetBalanceResult;
import com.stephanofer.progressengine.api.result.SetBalanceResult;
import com.stephanofer.progressengine.api.result.TransferResult;
import com.stephanofer.progressengine.api.source.ActorType;
import com.stephanofer.progressengine.api.source.OperationActor;
import com.stephanofer.progressengine.config.CommandSettings;
import com.stephanofer.progressengine.config.ConfigurationManager;
import com.stephanofer.progressengine.config.ConfigurationReloadResult;
import com.stephanofer.progressengine.config.ConfigurationSnapshot;
import com.stephanofer.progressengine.feedback.FeedbackService;
import com.stephanofer.progressengine.identity.PlayerIdentityRenderer;
import com.stephanofer.progressengine.lifecycle.InFlightCounts;
import com.stephanofer.progressengine.lifecycle.InFlightTracker;
import com.stephanofer.progressengine.lifecycle.RuntimeState;
import com.stephanofer.progressengine.localization.LocalizedMessages;
import com.stephanofer.progressengine.localization.MessageArguments;
import com.stephanofer.progressengine.persistence.CommandIntent;
import com.stephanofer.progressengine.persistence.CommandIntentDraft;
import com.stephanofer.progressengine.persistence.CommandIntentState;
import com.stephanofer.progressengine.persistence.CommandIntentType;
import com.stephanofer.progressengine.persistence.HistoryEntry;
import com.stephanofer.progressengine.persistence.HistoryPage;
import com.stephanofer.progressengine.persistence.LedgerCursor;
import com.stephanofer.progressengine.persistence.OperationalSnapshot;
import com.stephanofer.progressengine.persistence.ProgressPersistence;
import com.stephanofer.progressengine.synchronization.RedisSyncStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

public final class PointsCommands implements AutoCloseable {
    private final JavaPlugin plugin;
    private final PaperCommandManager.Bootstrapped<Source> manager;
    private final PointsClient points;
    private final ProgressPersistence persistence;
    private final ConfigurationManager configurationManager;
    private final BalanceStore balanceStore;
    private final InFlightTracker inFlightTracker;
    private final Supplier<RuntimeState> stateSupplier;
    private final Supplier<ConfigurationSnapshot> snapshotSupplier;
    private final Supplier<Optional<RedisSyncStatus>> redisStatusSupplier;
    private final LocalizedMessages messages;
    private final PlayerIdentityRenderer identities;
    private final CommandResponder responder;
    private final KnownPlayerSuggestionIndex suggestions;
    private final CommandTargetResolver targets;
    private final CommandTokenGenerator tokens = new CommandTokenGenerator();
    private final Logger logger;
    private final Clock clock;
    private org.bukkit.scheduler.BukkitTask cleanupTask;

    public PointsCommands(JavaPlugin plugin, PaperCommandManager.Bootstrapped<Source> manager, PointsClient points,
                          ProgressPersistence persistence, ConfigurationManager configurationManager,
                          BalanceStore balanceStore, InFlightTracker inFlightTracker,
                          Supplier<RuntimeState> stateSupplier, Supplier<ConfigurationSnapshot> snapshotSupplier,
                          Supplier<Optional<RedisSyncStatus>> redisStatusSupplier, LocalizedMessages messages,
                          PlayerIdentityRenderer identities, FeedbackService feedback, Logger logger, Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.points = Objects.requireNonNull(points, "points");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.configurationManager = Objects.requireNonNull(configurationManager, "configurationManager");
        this.balanceStore = Objects.requireNonNull(balanceStore, "balanceStore");
        this.inFlightTracker = Objects.requireNonNull(inFlightTracker, "inFlightTracker");
        this.stateSupplier = Objects.requireNonNull(stateSupplier, "stateSupplier");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.redisStatusSupplier = Objects.requireNonNull(redisStatusSupplier, "redisStatusSupplier");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.responder = new CommandResponder(plugin, feedback, logger);
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.suggestions = new KnownPlayerSuggestionIndex(plugin, persistence, () -> settings(), logger);
        this.targets = new CommandTargetResolver(plugin.getServer(), persistence, this.suggestions);
    }

    public void register() {
        this.suggestions.start();
        registerCommands();
        this.cleanupTask = this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(
            this.plugin,
            () -> this.persistence.commandIntents().deleteExpired(Instant.now(this.clock), 1000),
            20L * 60L,
            20L * 300L
        );
    }

    private void registerCommands() {
        CommandSettings settings = settings();
        String root = settings.registration().root();
        String[] aliases = settings.registration().aliases().toArray(String[]::new);
        command(this.manager.commandBuilder(root, aliases).permission(permission(CommandSettings.CommandPermission.BALANCE))
            .commandDescription(Description.of("Show your points balance"))
            .handler(context -> balanceSelf(context.sender().source())));
        command(this.manager.commandBuilder(root, aliases).literal("balance").permission(permission(CommandSettings.CommandPermission.BALANCE))
            .optional("target", StringParser.stringParser(), playerSuggestions())
            .handler(this::balance));
        command(this.manager.commandBuilder(root, aliases).literal("pay").permission(permission(CommandSettings.CommandPermission.PAY))
            .required("target", StringParser.stringParser(), playerSuggestions())
            .required("amount", StringParser.stringParser())
            .handler(this::pay));
        command(this.manager.commandBuilder(root, aliases).literal("pay").literal("confirm").permission(permission(CommandSettings.CommandPermission.PAY))
            .required("token", StringParser.stringParser()).handler(context -> confirmPay(context, false)));
        command(this.manager.commandBuilder(root, aliases).literal("pay").literal("retry").permission(permission(CommandSettings.CommandPermission.PAY))
            .required("token", StringParser.stringParser()).handler(context -> confirmPay(context, true)));
        command(this.manager.commandBuilder(root, aliases).literal("history").permission(permission(CommandSettings.CommandPermission.HISTORY))
            .optional("page", StringParser.stringParser()).handler(this::historySelf));
        command(this.manager.commandBuilder(root, aliases).literal("help").permission(permission(CommandSettings.CommandPermission.HELP))
            .handler(context -> help(context.sender().source())));

        command(this.manager.commandBuilder(root, aliases).literal("admin").literal("add").permission(permission(CommandSettings.CommandPermission.ADMIN_ADD))
            .required("target", StringParser.stringParser(), playerSuggestions()).required("amount", StringParser.stringParser())
            .optional("reason", StringParser.stringParser()).handler(context -> adminMutation(context, CommandIntentType.ADMIN_ADD)));
        command(this.manager.commandBuilder(root, aliases).literal("admin").literal("remove").permission(permission(CommandSettings.CommandPermission.ADMIN_REMOVE))
            .required("target", StringParser.stringParser(), playerSuggestions()).required("amount", StringParser.stringParser())
            .optional("reason", StringParser.stringParser()).handler(context -> adminMutation(context, CommandIntentType.ADMIN_REMOVE)));
        command(this.manager.commandBuilder(root, aliases).literal("admin").literal("set").permission(permission(CommandSettings.CommandPermission.ADMIN_SET))
            .required("target", StringParser.stringParser(), playerSuggestions()).required("amount", StringParser.stringParser())
            .optional("reason", StringParser.stringParser()).handler(context -> adminMutation(context, CommandIntentType.ADMIN_SET)));
        command(this.manager.commandBuilder(root, aliases).literal("admin").literal("reset").permission(permission(CommandSettings.CommandPermission.ADMIN_RESET))
            .required("target", StringParser.stringParser(), playerSuggestions()).optional("reason", StringParser.stringParser())
            .handler(context -> adminMutation(context, CommandIntentType.ADMIN_RESET)));
        command(this.manager.commandBuilder(root, aliases).literal("admin").literal("retry").permission(permission(CommandSettings.CommandPermission.ADMIN_ADD))
            .required("token", StringParser.stringParser()).handler(context -> confirmAdmin(context)));
        command(this.manager.commandBuilder(root, aliases).literal("admin").literal("history").permission(permission(CommandSettings.CommandPermission.ADMIN_HISTORY))
            .required("target", StringParser.stringParser(), playerSuggestions()).optional("page", StringParser.stringParser())
            .handler(this::historyAdmin));
        command(this.manager.commandBuilder(root, aliases).literal("admin").literal("reload").permission(permission(CommandSettings.CommandPermission.ADMIN_RELOAD))
            .handler(context -> reload(context.sender().source())));
        command(this.manager.commandBuilder(root, aliases).literal("admin").literal("status").permission(permission(CommandSettings.CommandPermission.ADMIN_STATUS))
            .handler(context -> status(context.sender().source())));
    }

    private void command(Command.Builder<Source> builder) {
        this.manager.command(builder);
    }

    private SuggestionProvider<Source> playerSuggestions() {
        return SuggestionProvider.blockingStrings((context, input) -> this.suggestions.suggestions(input.lastRemainingToken()));
    }

    private String permission(CommandSettings.CommandPermission permission) {
        return settings().permissions().require(permission);
    }

    private CommandSettings settings() {
        return this.snapshotSupplier.get().commands();
    }

    private void balance(CommandContext<Source> context) {
        if (!feature(context.sender().source(), CommandSettings.CommandFeature.BALANCE)) return;
        Optional<String> target = context.optional("target");
        if (target.isEmpty()) {
            balanceSelf(context.sender().source());
            return;
        }
        CommandSender sender = context.sender().source();
        if (!sender.hasPermission(permission(CommandSettings.CommandPermission.BALANCE_OTHERS))) {
            this.responder.send(sender, "command-no-permission");
            return;
        }
        this.targets.playerTarget(target.orElseThrow()).thenCompose(resolved -> {
            if (resolved.isEmpty()) {
                this.responder.send(sender, "unknown-target", MessageArguments.builder().unparsed("target", target.orElseThrow()).build());
                return CompletableFuture.completedFuture(null);
            }
            ResolvedTarget value = resolved.orElseThrow();
            return this.points.load(value.playerId()).thenCompose(snapshot -> identity(value).thenAccept(identity -> sendBalance(sender, "balance-other", snapshot, identity)));
        }).exceptionally(failure -> infrastructure(sender, failure));
    }

    private void balanceSelf(CommandSender sender) {
        if (!feature(sender, CommandSettings.CommandFeature.BALANCE)) return;
        if (!(sender instanceof Player player)) {
            this.responder.send(sender, "command-player-only");
            return;
        }
        if (!this.points.isReady(player.getUniqueId())) {
            this.responder.send(sender, "points-loading");
            return;
        }
        this.points.cached(player.getUniqueId()).ifPresentOrElse(
            snapshot -> sendBalance(sender, "balance-self", snapshot, Component.empty()),
            () -> this.responder.send(sender, "points-loading")
        );
    }

    private void sendBalance(CommandSender sender, String key, BalanceSnapshot snapshot, Component target) {
        String language = consoleLanguage();
        MessageArguments.Builder args = MessageArguments.builder()
            .unparsed("balance", this.messages.formatted(snapshot.balance(), language))
            .unparsed("balance_raw", this.messages.raw(snapshot.balance()))
            .unparsed("balance_compact", this.messages.compact(snapshot.balance(), language));
        if (!target.equals(Component.empty())) args.component("target", target);
        this.responder.send(sender, key, args.build());
    }

    private void pay(CommandContext<Source> context) {
        CommandSender sender = context.sender().source();
        if (!(sender instanceof Player player)) {
            this.responder.send(sender, "command-player-only");
            return;
        }
        if (!feature(sender, CommandSettings.CommandFeature.PAY)) return;
        if (!this.points.isReady(player.getUniqueId())) {
            this.responder.send(sender, "points-loading");
            return;
        }
        String rawAmount = context.get("amount");
        long amount;
        try {
            amount = CommandParsers.positiveAmount(rawAmount, settings().pay().maximum());
        } catch (IllegalArgumentException exception) {
            this.responder.send(sender, "invalid-amount", MessageArguments.builder().unparsed("input", rawAmount).build());
            return;
        }
        CommandSettings.Pay pay = settings().pay();
        if (amount < pay.minimum() || amount > pay.maximum()) {
            this.responder.send(sender, "pay-range", MessageArguments.builder()
                .unparsed("minimum", Long.toString(pay.minimum())).unparsed("maximum", Long.toString(pay.maximum())).build());
            return;
        }
        String rawTarget = context.get("target");
        this.targets.playerTarget(rawTarget).thenCompose(target -> {
            if (target.isEmpty()) {
                this.responder.send(sender, "unknown-target", MessageArguments.builder().unparsed("target", rawTarget).build());
                return CompletableFuture.completedFuture(null);
            }
            ResolvedTarget resolved = target.orElseThrow();
            if (resolved.playerId().equals(player.getUniqueId())) {
                this.responder.send(sender, "target-self");
                return CompletableFuture.completedFuture(null);
            }
            return checkCooldown(player).thenCompose(cooldown -> {
                if (cooldown > 0L) {
                    this.responder.send(sender, "pay-cooldown", MessageArguments.builder().unparsed("seconds", Long.toString(cooldown)).build());
                    return CompletableFuture.completedFuture(null);
                }
                BalanceSnapshot observed = this.points.cached(player.getUniqueId()).orElse(null);
                if (observed == null) {
                    this.responder.send(sender, "points-loading");
                    return CompletableFuture.completedFuture(null);
                }
                boolean needsConfirmation = pay.confirmation().enabled() && amount >= pay.confirmation().threshold();
                return createIntent(CommandIntentType.PAY, player, Optional.of(player.getUniqueId()), resolved.playerId(), amount,
                    settings().reasons().playerTransfer(), Optional.of(observed.revision()),
                    needsConfirmation ? CommandIntentState.AWAITING_CONFIRMATION : CommandIntentState.SUBMITTED)
                    .thenCompose(created -> needsConfirmation
                        ? identity(resolved).thenAccept(identity -> this.responder.send(sender, "pay-confirm-required", MessageArguments.builder()
                            .unparsed("amount", Long.toString(amount)).component("target", identity).unparsed("token", created.token()).build()))
                        : executeIntent(sender, created.intent(), created.token()));
            });
        }).exceptionally(failure -> infrastructure(sender, failure));
    }

    private void confirmPay(CommandContext<Source> context, boolean retry) {
        CommandSender sender = context.sender().source();
        if (!(sender instanceof Player player)) {
            this.responder.send(sender, "command-player-only");
            return;
        }
        submitToken(sender, context.get("token"), CommandIntentType.PAY, Optional.of(player.getUniqueId()), true);
    }

    private void confirmAdmin(CommandContext<Source> context) {
        CommandSender sender = context.sender().source();
        submitToken(sender, context.get("token"), null, owner(sender), false);
    }

    private void submitToken(CommandSender sender, String token, CommandIntentType expectedType, Optional<UUID> owner, boolean pay) {
        Instant now = Instant.now(this.clock);
        byte[] hash = this.tokens.hash(token);
        this.persistence.commandIntents().find(hash).thenCompose(found -> {
            if (found.isEmpty()) {
                this.responder.send(sender, pay ? "pay-token-invalid" : "pay-token-invalid", MessageArguments.builder().unparsed("token", token).build());
                return CompletableFuture.completedFuture(null);
            }
            CommandIntent intent = found.orElseThrow();
            if (expectedType != null && intent.type() != expectedType) {
                this.responder.send(sender, "pay-token-invalid", MessageArguments.builder().unparsed("token", token).build());
                return CompletableFuture.completedFuture(null);
            }
            if (!intent.ownerId().equals(owner)) {
                this.responder.send(sender, "pay-token-invalid", MessageArguments.builder().unparsed("token", token).build());
                return CompletableFuture.completedFuture(null);
            }
            if (intent.expiredAt(now) && intent.state() == CommandIntentState.AWAITING_CONFIRMATION) {
                this.responder.send(sender, "pay-token-expired", MessageArguments.builder().unparsed("token", token).build());
                return CompletableFuture.completedFuture(null);
            }
            if (intent.state() == CommandIntentState.AWAITING_CONFIRMATION && intent.observedRevision().isPresent()) {
                Optional<BalanceSnapshot> current = this.points.cached(intent.playerId());
                if (current.isEmpty() || current.orElseThrow().revision() != intent.observedRevision().orElseThrow()) {
                    this.responder.send(sender, "pay-token-stale");
                    return CompletableFuture.completedFuture(null);
                }
            }
            Instant expiry = now.plusSeconds(settings().pay().retryRetentionSeconds());
            return this.persistence.commandIntents().markSubmitted(hash, now, expiry)
                .thenCompose(submitted -> submitted.map(value -> executeIntent(sender, value, token)).orElseGet(() -> CompletableFuture.completedFuture(null)));
        }).exceptionally(failure -> infrastructure(sender, failure));
    }

    private CompletableFuture<CreatedIntent> createIntent(CommandIntentType type, Player actor, Optional<UUID> ownerId,
                                                         UUID receiverId, long amount, OperationReason reason,
                                                         Optional<Long> observedRevision, CommandIntentState state) {
        String token = this.tokens.generate();
        Instant now = Instant.now(this.clock);
        long seconds = state == CommandIntentState.AWAITING_CONFIRMATION
            ? settings().pay().confirmation().expirySeconds()
            : settings().pay().retryRetentionSeconds();
        CommandIntentDraft draft = new CommandIntentDraft(
            this.tokens.hash(token), OperationId.generate(), type, state, ownerId, ActorType.PLAYER, Optional.of(actor.getUniqueId()),
            actor.getUniqueId(), Optional.of(receiverId), amount, reason, observedRevision,
            this.snapshotSupplier.get().config().serverId(), now, now.plusSeconds(seconds)
        );
        return this.persistence.commandIntents().insert(draft).thenApply(ignored -> new CreatedIntent(token, toIntent(draft)));
    }

    private CommandIntent toIntent(CommandIntentDraft draft) {
        Optional<Instant> submitted = draft.state() == CommandIntentState.SUBMITTED ? Optional.of(draft.createdAt()) : Optional.empty();
        return new CommandIntent(draft.tokenHash(), draft.operationId(), draft.type(), draft.state(), draft.ownerId(), draft.actorType(),
            draft.actorId(), draft.playerId(), draft.targetId(), draft.amount(), draft.reason(), draft.observedRevision(), draft.sourceServerId(),
            draft.createdAt(), draft.expiresAt(), submitted, Optional.empty());
    }

    private CompletableFuture<Void> executeIntent(CommandSender sender, CommandIntent intent, String token) {
        CompletableFuture<?> result = switch (intent.type()) {
            case PAY -> this.points.transfer(new TransferRequest(intent.operationId(), intent.playerId(), intent.targetId().orElseThrow(),
                intent.amount(), intent.reason(), intent.actor(), metadata("pay")));
            case ADMIN_ADD -> this.points.credit(new CreditRequest(intent.operationId(), intent.playerId(), intent.amount(), intent.reason(), intent.actor(), metadata("admin_add")));
            case ADMIN_REMOVE -> this.points.debit(new DebitRequest(intent.operationId(), intent.playerId(), intent.amount(), intent.reason(), intent.actor(), metadata("admin_remove")));
            case ADMIN_SET -> this.points.setBalance(new SetBalanceRequest(intent.operationId(), intent.playerId(), intent.amount(), intent.reason(), intent.actor(), metadata("admin_set")));
            case ADMIN_RESET -> this.points.resetBalance(new ResetBalanceRequest(intent.operationId(), intent.playerId(), intent.reason(), intent.actor(), metadata("admin_reset")));
        };
        return result.thenCompose(value -> this.persistence.commandIntents().markResolved(intent.tokenHash(), Instant.now(this.clock))
            .thenCompose(ignored -> renderResult(sender, intent, value))).exceptionally(failure -> {
                this.logger.log(Level.WARNING, "ProgressEngine command intent has ambiguous result: " + intent.operationId(), failure);
                this.responder.send(sender, intent.type() == CommandIntentType.PAY ? "pay-retry-available" : "admin-retry-available",
                    MessageArguments.builder().unparsed("token", token).build());
                return null;
            });
    }

    private CompletableFuture<Void> renderResult(CommandSender sender, CommandIntent intent, Object value) {
        if (value instanceof TransferResult.Success success) {
            BalanceSnapshot senderSnapshot = snapshotFor(success.receipt().changes().get(0).playerId(), success.receipt().changes().get(0).balanceAfter(), success.receipt().changes().get(0).revision());
            return this.persistence.playerNames().findByPlayerId(intent.targetId().orElseThrow())
                .thenCompose(name -> this.identities.renderOffline(intent.targetId().orElseThrow(), name.map(com.stephanofer.progressengine.persistence.KnownPlayerName::username)))
                .thenAccept(target -> this.responder.send(sender, "pay-success-sender", balanceArgs(senderSnapshot.balance()).component("target", target)
                    .unparsed("amount", Long.toString(intent.amount())).build()));
        }
        if (value instanceof TransferResult.InsufficientFunds) {
            return this.points.refresh(intent.playerId()).thenAccept(snapshot -> this.responder.send(sender, "pay-insufficient-funds",
                balanceArgs(snapshot.balance()).unparsed("amount", Long.toString(intent.amount())).build()));
        }
        if (value instanceof TransferResult.BalanceLimitExceeded) {
            return identity(new ResolvedTarget(intent.targetId().orElseThrow(), Optional.empty())).thenAccept(target -> this.responder.send(sender, "pay-balance-limit",
                MessageArguments.builder().component("target", target).unparsed("amount", Long.toString(intent.amount())).build()));
        }
        if (value instanceof CreditResult.Success success) return adminSuccess(sender, "admin-add-success", success.receipt().changes().get(0).balanceAfter(), intent);
        if (value instanceof DebitResult.Success success) return adminSuccess(sender, "admin-remove-success", success.receipt().changes().get(0).balanceAfter(), intent);
        if (value instanceof SetBalanceResult.Success success) return adminSuccess(sender, "admin-set-success", success.receipt().changes().get(0).balanceAfter(), intent);
        if (value instanceof ResetBalanceResult.Success success) return adminSuccess(sender, "admin-reset-success", success.receipt().changes().get(0).balanceAfter(), intent);
        this.responder.send(sender, "infrastructure-unavailable");
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> adminSuccess(CommandSender sender, String key, long balance, CommandIntent intent) {
        return identity(new ResolvedTarget(intent.playerId(), Optional.empty())).thenAccept(target -> this.responder.send(sender, key,
            balanceArgs(balance).component("target", target).unparsed("amount", Long.toString(intent.amount())).build()));
    }

    private BalanceSnapshot snapshotFor(UUID playerId, long balance, long revision) {
        return new BalanceSnapshot(playerId, balance, revision, Instant.now(this.clock));
    }

    private OperationMetadata metadata(String command) {
        return OperationMetadata.of(Map.of("command", command));
    }

    private MessageArguments.Builder balanceArgs(long balance) {
        String language = consoleLanguage();
        return MessageArguments.builder()
            .unparsed("balance", this.messages.formatted(balance, language))
            .unparsed("balance_raw", this.messages.raw(balance))
            .unparsed("balance_compact", this.messages.compact(balance, language));
    }

    private CompletableFuture<Long> checkCooldown(Player player) {
        long cooldown = settings().pay().cooldownSeconds();
        if (cooldown == 0L) return CompletableFuture.completedFuture(0L);
        return this.persistence.commandIntents().lastSuccessfulPlayerPay(player.getUniqueId()).thenApply(last -> {
            if (last.isEmpty()) return 0L;
            long elapsed = Duration.between(last.orElseThrow(), Instant.now(this.clock)).toSeconds();
            return Math.max(0L, cooldown - elapsed);
        });
    }

    private void adminMutation(CommandContext<Source> context, CommandIntentType type) {
        CommandSender sender = context.sender().source();
        if (!feature(sender, featureFor(type))) return;
        if (!adminSender(sender)) return;
        String rawTarget = context.get("target");
        String rawAmount = context.<String>optional("amount").orElse("0");
        long amount;
        try {
            amount = type == CommandIntentType.ADMIN_RESET ? 0L : (type == CommandIntentType.ADMIN_SET
                ? CommandParsers.nonNegativeAmount(rawAmount, this.snapshotSupplier.get().config().economy().maximumBalance())
                : CommandParsers.positiveAmount(rawAmount, this.snapshotSupplier.get().config().economy().maximumBalance()));
        } catch (IllegalArgumentException exception) {
            this.responder.send(sender, "invalid-amount", MessageArguments.builder().unparsed("input", rawAmount).build());
            return;
        }
        OperationReason reason;
        try {
            reason = context.<String>optional("reason").map(CommandParsers::reason).orElse(defaultReason(type));
        } catch (IllegalArgumentException exception) {
            this.responder.send(sender, "invalid-reason", MessageArguments.builder().unparsed("input", context.<String>optional("reason").orElse("")).build());
            return;
        }
        this.targets.administrativeTarget(rawTarget).thenCompose(target -> {
            if (target.isEmpty()) {
                this.responder.send(sender, "unknown-target", MessageArguments.builder().unparsed("target", rawTarget).build());
                return CompletableFuture.completedFuture(null);
            }
            ResolvedTarget resolved = target.orElseThrow();
            return createAdminIntent(type, sender, resolved.playerId(), amount, reason).thenCompose(created -> executeIntent(sender, created.intent(), created.token()));
        }).exceptionally(failure -> infrastructure(sender, failure));
    }

    private CompletableFuture<CreatedIntent> createAdminIntent(CommandIntentType type, CommandSender sender, UUID targetId, long amount, OperationReason reason) {
        String token = this.tokens.generate();
        Instant now = Instant.now(this.clock);
        Optional<UUID> ownerId = owner(sender);
        ActorType actorType = sender instanceof Player ? ActorType.PLAYER : ActorType.CONSOLE;
        Optional<UUID> actorId = sender instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.empty();
        CommandIntentDraft draft = new CommandIntentDraft(this.tokens.hash(token), OperationId.generate(), type, CommandIntentState.SUBMITTED,
            ownerId, actorType, actorId, targetId, Optional.empty(), amount, reason, Optional.empty(),
            this.snapshotSupplier.get().config().serverId(), now, now.plusSeconds(settings().pay().retryRetentionSeconds()));
        return this.persistence.commandIntents().insert(draft).thenApply(ignored -> new CreatedIntent(token, toIntent(draft)));
    }

    private Optional<UUID> owner(CommandSender sender) {
        return sender instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.empty();
    }

    private OperationReason defaultReason(CommandIntentType type) {
        return switch (type) {
            case PAY -> settings().reasons().playerTransfer();
            case ADMIN_ADD -> settings().reasons().adminAdd();
            case ADMIN_REMOVE -> settings().reasons().adminRemove();
            case ADMIN_SET -> settings().reasons().adminSet();
            case ADMIN_RESET -> settings().reasons().adminReset();
        };
    }

    private boolean adminSender(CommandSender sender) {
        if (sender instanceof Player || sender instanceof ConsoleCommandSender) return true;
        this.responder.send(sender, "command-player-or-console-only");
        return false;
    }

    private void historySelf(CommandContext<Source> context) {
        CommandSender sender = context.sender().source();
        if (!feature(sender, CommandSettings.CommandFeature.HISTORY)) return;
        if (!(sender instanceof Player player)) {
            this.responder.send(sender, "command-player-only");
            return;
        }
        int page = parsePage(sender, context.optional("page"));
        if (page < 1) return;
        loadHistory(sender, player.getUniqueId(), page, false);
    }

    private void historyAdmin(CommandContext<Source> context) {
        CommandSender sender = context.sender().source();
        if (!feature(sender, CommandSettings.CommandFeature.ADMIN_HISTORY)) return;
        int page = parsePage(sender, context.optional("page"));
        if (page < 1) return;
        String target = context.get("target");
        this.targets.administrativeTarget(target).thenAccept(resolved -> {
            if (resolved.isEmpty()) this.responder.send(sender, "unknown-target", MessageArguments.builder().unparsed("target", target).build());
            else loadHistory(sender, resolved.orElseThrow().playerId(), page, true);
        }).exceptionally(failure -> infrastructure(sender, failure));
    }

    private int parsePage(CommandSender sender, Optional<String> page) {
        if (page.isEmpty()) return 1;
        try {
            return CommandParsers.page(page.orElseThrow());
        } catch (IllegalArgumentException exception) {
            this.responder.send(sender, "invalid-page", MessageArguments.builder().unparsed("input", page.orElseThrow()).build());
            return -1;
        }
    }

    private void loadHistory(CommandSender sender, UUID playerId, int page, boolean admin) {
        loadHistoryPage(playerId, page, 1, Optional.empty())
            .thenAccept(history -> renderHistory(sender, history, page))
            .exceptionally(failure -> infrastructure(sender, failure));
    }

    private CompletableFuture<HistoryPage> loadHistoryPage(UUID playerId, int targetPage, int currentPage, Optional<LedgerCursor> cursor) {
        return this.persistence.history().history(playerId, settings().history().pageSize(), cursor).thenCompose(page -> {
            if (currentPage >= targetPage || page.nextCursor().isEmpty()) {
                return CompletableFuture.completedFuture(page);
            }
            return loadHistoryPage(playerId, targetPage, currentPage + 1, page.nextCursor());
        });
    }

    private void renderHistory(CommandSender sender, HistoryPage history, int page) {
        if (history.entries().isEmpty()) {
            this.responder.send(sender, "history-empty");
            return;
        }
        this.responder.send(sender, "history-header", MessageArguments.builder().unparsed("page", Integer.toString(page)).build());
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(settings().history().timeZone());
        for (HistoryEntry entry : history.entries()) {
            this.responder.send(sender, "history-entry", MessageArguments.builder()
                .unparsed("date", formatter.format(entry.createdAt()))
                .unparsed("amount", Long.toString(entry.delta()))
                .unparsed("balance", Long.toString(entry.balanceAfter()))
                .unparsed("reason", entry.reason().value())
                .unparsed("type", entry.type().name())
                .unparsed("operation", entry.operationId().toString())
                .build());
        }
        history.nextCursor().ifPresent(cursor -> this.responder.send(sender, "history-next-page", MessageArguments.builder().unparsed("page", Integer.toString(page + 1)).build()));
        if (page > 1) this.responder.send(sender, "history-previous-page", MessageArguments.builder().unparsed("page", Integer.toString(page - 1)).build());
    }

    private void reload(CommandSender sender) {
        if (!feature(sender, CommandSettings.CommandFeature.ADMIN_RELOAD)) return;
        this.configurationManager.reloadAsync().thenAccept(result -> {
            if (!result.success()) {
                this.responder.send(sender, "admin-reload-failure", MessageArguments.builder()
                    .unparsed("problems", problems(result)).build());
                return;
            }
            this.responder.send(sender, "admin-reload-success", MessageArguments.builder()
                .unparsed("revision", Long.toString(result.activeSnapshot().orElseThrow().revision()))
                .unparsed("applied", String.join(", ", result.changes().applied()))
                .build());
            if (!result.changes().restartRequired().isEmpty()) {
                this.responder.send(sender, "admin-reload-restart-required", MessageArguments.builder()
                    .unparsed("changes", String.join(", ", result.changes().restartRequired()))
                    .build());
            }
        }).exceptionally(failure -> infrastructure(sender, failure));
    }

    private String problems(ConfigurationReloadResult result) {
        return result.problems().stream()
            .limit(5)
            .map(problem -> problem.path() + '=' + problem.message())
            .reduce((left, right) -> left + ", " + right)
            .orElse("unknown");
    }

    private void status(CommandSender sender) {
        if (!feature(sender, CommandSettings.CommandFeature.ADMIN_STATUS)) return;
        CompletableFuture<OperationalSnapshot> db = this.persistence.operational().snapshot();
        db.thenAccept(snapshot -> {
            this.responder.send(sender, "admin-status-header", MessageArguments.builder().unparsed("state", this.stateSupplier.get().name()).build());
            statusLine(sender, "configuration", Long.toString(this.snapshotSupplier.get().revision()));
            statusLine(sender, "mysql", snapshot.databaseHealthy() ? "healthy " + snapshot.probeLatency().toMillis() + "ms" : "unhealthy");
            statusLine(sender, "schema", snapshot.schemaVersion().orElse("unknown"));
            Optional<RedisSyncStatus> redis = this.redisStatusSupplier.get();
            statusLine(sender, "redis", redis.map(value -> value.redis().state().name()).orElse("unavailable"));
            redis.ifPresent(value -> {
                statusLine(sender, "redis subscriptions", value.redis().activeSubscriptions() + "/" + value.redis().requestedSubscriptions());
                statusLine(sender, "reconciliation", value.reconciliationRunning() + " interval=" + value.effectiveIntervalSeconds() + "s");
                statusLine(sender, "redis failed publications", Long.toString(value.failedPublications()));
                statusLine(sender, "redis invalid payloads", Long.toString(value.invalidPayloads()));
            });
            CacheStats stats = this.balanceStore.stats();
            statusLine(sender, "cache size", Long.toString(this.balanceStore.estimatedSize()));
            statusLine(sender, "cache hit rate", String.format(java.util.Locale.ROOT, "%.2f", stats.hitRate()));
            InFlightCounts counts = this.inFlightTracker.counts();
            statusLine(sender, "in flight", "loads=" + counts.loads() + " mutations=" + counts.mutations());
        }).exceptionally(failure -> infrastructure(sender, failure));
    }

    private void statusLine(CommandSender sender, String label, String value) {
        this.responder.send(sender, "admin-status-line", MessageArguments.builder().unparsed("label", label).unparsed("value", value).build());
    }

    private void help(CommandSender sender) {
        if (!feature(sender, CommandSettings.CommandFeature.HELP)) return;
        statusLine(sender, "commands", "/" + settings().registration().root() + " balance | pay | history | admin");
    }

    private CommandSettings.CommandFeature featureFor(CommandIntentType type) {
        return switch (type) {
            case PAY -> CommandSettings.CommandFeature.PAY;
            case ADMIN_ADD -> CommandSettings.CommandFeature.ADMIN_ADD;
            case ADMIN_REMOVE -> CommandSettings.CommandFeature.ADMIN_REMOVE;
            case ADMIN_SET -> CommandSettings.CommandFeature.ADMIN_SET;
            case ADMIN_RESET -> CommandSettings.CommandFeature.ADMIN_RESET;
        };
    }

    private boolean feature(CommandSender sender, CommandSettings.CommandFeature feature) {
        if (settings().availability().enabled(feature)) return true;
        this.responder.send(sender, "command-disabled", MessageArguments.builder().unparsed("command", feature.configKey()).build());
        return false;
    }

    private CompletableFuture<Component> identity(ResolvedTarget target) {
        return this.identities.renderOffline(target.playerId(), target.username());
    }

    private String consoleLanguage() {
        return this.snapshotSupplier.get().localization().consoleLanguage();
    }

    private Void infrastructure(CommandSender sender, Throwable failure) {
        this.logger.log(Level.WARNING, "ProgressEngine command failed", failure);
        this.responder.send(sender, "infrastructure-unavailable");
        return null;
    }

    @Override
    public void close() {
        if (this.cleanupTask != null) this.cleanupTask.cancel();
        this.suggestions.close();
    }

    private record CreatedIntent(String token, CommandIntent intent) {
    }
}
