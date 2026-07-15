package com.stephanofer.progressengine;

import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;
import com.stephanofer.networkplayersettings.assets.api.CountryFlagService;
import com.stephanofer.networkplayersettings.settings.api.PlayerStyleService;
import com.stephanofer.progressengine.account.AccountEconomy;
import com.stephanofer.progressengine.account.BalanceStore;
import com.stephanofer.progressengine.account.BukkitPostCommitEventDispatcher;
import com.stephanofer.progressengine.account.PostCommitPublisher;
import com.stephanofer.progressengine.api.source.OperationSource;
import com.stephanofer.progressengine.award.AwardCoordinator;
import com.stephanofer.progressengine.award.BukkitAwardPrepareEventDispatcher;
import com.stephanofer.progressengine.booster.NetworkBoostersIntegration;
import com.stephanofer.progressengine.booster.NetworkBoostersIntegrationFactory;
import com.stephanofer.progressengine.config.BoostedYamlConfigurationLoader;
import com.stephanofer.progressengine.config.ConfigurationManager;
import com.stephanofer.progressengine.config.ConfigurationProblem;
import com.stephanofer.progressengine.config.ConfigurationReloadResult;
import com.stephanofer.progressengine.config.ConfigurationSnapshot;
import com.stephanofer.progressengine.config.ProgressEngineConfig;
import com.stephanofer.progressengine.feedback.AwardFeedbackCoalescer;
import com.stephanofer.progressengine.feedback.AwardFeedbackListener;
import com.stephanofer.progressengine.feedback.BossBarManager;
import com.stephanofer.progressengine.feedback.FeedbackService;
import com.stephanofer.progressengine.identity.IdentityInvalidationListener;
import com.stephanofer.progressengine.identity.PlayerIdentityRenderer;
import com.stephanofer.progressengine.lifecycle.BukkitPlayerLifecycleListener;
import com.stephanofer.progressengine.lifecycle.InFlightTracker;
import com.stephanofer.progressengine.lifecycle.LifecycleResources;
import com.stephanofer.progressengine.lifecycle.PlayerLifecycleCoordinator;
import com.stephanofer.progressengine.lifecycle.RuntimeLifecycle;
import com.stephanofer.progressengine.lifecycle.RuntimeState;
import com.stephanofer.progressengine.localization.LocalizedMessages;
import com.stephanofer.progressengine.persistence.ProgressDatabaseFactory;
import com.stephanofer.progressengine.persistence.ProgressPersistence;
import com.stephanofer.progressengine.service.ProgressPointsService;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.luckperms.api.LuckPerms;

public final class ProgressEngineRuntime implements AutoCloseable {
    private final JavaPlugin plugin;
    private final RuntimeLifecycle lifecycle;
    private final InFlightTracker inFlightTracker;
    private final LifecycleResources resources;
    private final ConfigurationManager configurationManager;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final AtomicReference<ProgressPersistence> persistence = new AtomicReference<>();
    private final AtomicReference<BalanceStore> balanceStore = new AtomicReference<>();
    private final AtomicReference<AccountEconomy> accountEconomy = new AtomicReference<>();
    private final AtomicReference<PlayerLifecycleCoordinator> playerLifecycle = new AtomicReference<>();
    private final AtomicReference<ProgressPointsService> pointsService = new AtomicReference<>();
    private final AtomicReference<LocalizedMessages> localizedMessages = new AtomicReference<>();
    private final AtomicReference<PlayerIdentityRenderer> identityRenderer = new AtomicReference<>();
    private final AtomicReference<FeedbackService> feedbackService = new AtomicReference<>();

    private ProgressEngineRuntime(
        JavaPlugin plugin,
        RuntimeLifecycle lifecycle,
        InFlightTracker inFlightTracker,
        LifecycleResources resources,
        ConfigurationManager configurationManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.inFlightTracker = Objects.requireNonNull(inFlightTracker, "inFlightTracker");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.configurationManager = Objects.requireNonNull(configurationManager, "configurationManager");
        this.resources.register("configuration-manager", this.configurationManager);
    }

    public static ProgressEngineRuntime create(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        RuntimeLifecycle lifecycle = new RuntimeLifecycle();
        InFlightTracker inFlightTracker = new InFlightTracker(lifecycle);
        LifecycleResources resources = new LifecycleResources();
        Executor asyncExecutor = command -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, command);
        ConfigurationManager configurationManager = new ConfigurationManager(
            new BoostedYamlConfigurationLoader(plugin.getDataFolder().toPath(), plugin::getResource, Clock.systemUTC()),
            asyncExecutor
        );
        return new ProgressEngineRuntime(plugin, lifecycle, inFlightTracker, resources, configurationManager);
    }

    public RuntimeState state() {
        return this.lifecycle.state();
    }

    public ConfigurationManager configurationManager() {
        return this.configurationManager;
    }

    public InFlightTracker inFlightTracker() {
        return this.inFlightTracker;
    }

    public void start() {
        this.configurationManager.reloadAsync().whenComplete((result, failure) -> {
            if (this.lifecycle.state() == RuntimeState.SHUTTING_DOWN || this.lifecycle.state() == RuntimeState.CLOSED) {
                return;
            }
            if (failure != null) {
                this.plugin.getLogger().log(Level.SEVERE, "ProgressEngine failed to load configuration", unwrap(failure));
                disableOnMainThread();
                return;
            }
            if (!result.success()) {
                logProblems(result);
                disableOnMainThread();
                return;
            }
            initializePersistence(result.activeSnapshot().orElseThrow());
        });
    }

    public ProgressPersistence persistence() {
        ProgressPersistence value = this.persistence.get();
        if (value == null) {
            throw new IllegalStateException("ProgressEngine persistence is not initialized");
        }
        return value;
    }

    public BalanceStore balanceStore() {
        BalanceStore value = this.balanceStore.get();
        if (value == null) {
            throw new IllegalStateException("ProgressEngine balance store is not initialized");
        }
        return value;
    }

    public AccountEconomy accountEconomy() {
        AccountEconomy value = this.accountEconomy.get();
        if (value == null) {
            throw new IllegalStateException("ProgressEngine account economy is not initialized");
        }
        return value;
    }

    public PlayerLifecycleCoordinator playerLifecycle() {
        PlayerLifecycleCoordinator value = this.playerLifecycle.get();
        if (value == null) {
            throw new IllegalStateException("ProgressEngine player lifecycle is not initialized");
        }
        return value;
    }

    public ProgressPointsService pointsService() {
        ProgressPointsService value = this.pointsService.get();
        if (value == null) {
            throw new IllegalStateException("ProgressEngine points service is not initialized");
        }
        return value;
    }

    public LocalizedMessages localizedMessages() {
        LocalizedMessages value = this.localizedMessages.get();
        if (value == null) {
            throw new IllegalStateException("ProgressEngine localization is not initialized");
        }
        return value;
    }

    public PlayerIdentityRenderer identityRenderer() {
        PlayerIdentityRenderer value = this.identityRenderer.get();
        if (value == null) {
            throw new IllegalStateException("ProgressEngine identity renderer is not initialized");
        }
        return value;
    }

    public FeedbackService feedbackService() {
        FeedbackService value = this.feedbackService.get();
        if (value == null) {
            throw new IllegalStateException("ProgressEngine feedback service is not initialized");
        }
        return value;
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        if (!this.shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        RuntimeState current = this.lifecycle.state();
        if (current != RuntimeState.CLOSED && current != RuntimeState.SHUTTING_DOWN) {
            this.lifecycle.transitionTo(RuntimeState.SHUTTING_DOWN);
        }

        this.configurationManager.close();
        try {
            long timeoutSeconds = this.configurationManager.activeSnapshot()
                .map(snapshot -> snapshot.config().runtime().shutdownTimeoutSeconds())
                .orElse(10L);
            boolean drained = this.inFlightTracker.awaitDrained(Duration.ofSeconds(timeoutSeconds));
            if (!drained) {
                this.plugin.getLogger().warning("ProgressEngine shutdown timed out while waiting for in-flight work.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.plugin.getLogger().log(Level.WARNING, "ProgressEngine shutdown was interrupted while waiting for in-flight work", exception);
        }

        try {
            this.resources.close();
        } catch (RuntimeException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "ProgressEngine failed to close one or more runtime resources", exception);
        } finally {
            if (this.lifecycle.state() != RuntimeState.CLOSED) {
                this.lifecycle.transitionTo(RuntimeState.CLOSED);
            }
        }
    }

    private void logProblems(ConfigurationReloadResult result) {
        for (ConfigurationProblem problem : result.problems()) {
            this.plugin.getLogger().severe("Invalid ProgressEngine configuration at " + problem.path() + ": " + problem.message());
        }
    }

    private void initializePersistence(ConfigurationSnapshot snapshot) {
        if (this.lifecycle.state() == RuntimeState.SHUTTING_DOWN || this.lifecycle.state() == RuntimeState.CLOSED) {
            return;
        }
        ProgressPersistence created;
        try {
            created = ProgressDatabaseFactory.create(snapshot.config(), this.plugin.getClass().getClassLoader());
            if (!this.persistence.compareAndSet(null, created)) {
                created.close();
                throw new IllegalStateException("ProgressEngine persistence was already initialized");
            }
            this.resources.register("persistence", created);
        } catch (RuntimeException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "ProgressEngine failed to create persistence", exception);
            transitionDatabaseUnavailable();
            disableOnMainThread();
            return;
        }

        created.migrate().whenComplete((ignored, failure) -> {
            if (failure != null) {
                this.plugin.getLogger().log(Level.SEVERE, "ProgressEngine failed to migrate database", unwrap(failure));
                transitionDatabaseUnavailable();
                try {
                    created.close();
                } catch (RuntimeException closeFailure) {
                    this.plugin.getLogger().log(Level.SEVERE, "ProgressEngine failed to close persistence after migration failure", closeFailure);
                }
                disableOnMainThread();
                return;
            }
            runOnMainThread(() -> {
                try {
                    initializeEconomicRuntime(created, snapshot);
                } catch (RuntimeException exception) {
                    this.plugin.getLogger().log(Level.SEVERE, "ProgressEngine failed to initialize economic runtime", exception);
                    disableOnMainThread();
                }
            });
        });
    }

    private void initializeEconomicRuntime(ProgressPersistence persistence, ConfigurationSnapshot snapshot) {
        if (this.lifecycle.state() == RuntimeState.SHUTTING_DOWN || this.lifecycle.state() == RuntimeState.CLOSED) {
            return;
        }

        BalanceStore store = new BalanceStore(persistence, snapshot.config().cache(), this.inFlightTracker, Clock.systemUTC());
        PostCommitPublisher postCommitPublisher = new PostCommitPublisher(
            store,
            new BukkitPostCommitEventDispatcher(this.plugin, this.plugin.getLogger()),
            this.plugin.getLogger()
        );
        AccountEconomy economy = new AccountEconomy(
            persistence,
            new OperationSource(this.plugin.getName(), snapshot.config().serverId()),
            () -> this.configurationManager.activeSnapshot()
                .map(active -> active.config().economy().maximumBalance())
                .orElse(snapshot.config().economy().maximumBalance()),
            new com.stephanofer.progressengine.transaction.AccountMutationSequencer(),
            postCommitPublisher
        );
        PlayerSettingsService playerSettings = resolvePlayerSettingsService();
        PlayerStyleService playerStyles = resolveService(PlayerStyleService.class, "NetworkPlayerSettings PlayerStyleService");
        CountryFlagService countryFlags = resolveService(CountryFlagService.class, "NetworkPlayerSettings CountryFlagService");
        LuckPerms luckPerms = resolveService(LuckPerms.class, "LuckPerms service");
        LocalizedMessages localizedMessages = new LocalizedMessages(this::activeSnapshot);
        PlayerIdentityRenderer identityRenderer = new PlayerIdentityRenderer(
            luckPerms,
            playerStyles,
            countryFlags,
            this::activeSnapshot,
            this.plugin.getLogger()
        );
        BossBarManager bossBars = new BossBarManager(this.plugin);
        FeedbackService feedbackService = new FeedbackService(
            this.plugin,
            playerSettings,
            localizedMessages,
            bossBars,
            this::activeSnapshot,
            this.plugin.getLogger()
        );
        AwardFeedbackCoalescer awardFeedbackCoalescer = new AwardFeedbackCoalescer(
            this.plugin,
            feedbackService,
            this::activeSnapshot,
            this.plugin.getLogger()
        );
        AwardFeedbackListener awardFeedbackListener = new AwardFeedbackListener(awardFeedbackCoalescer);
        IdentityInvalidationListener identityInvalidationListener = new IdentityInvalidationListener(this.plugin, luckPerms, identityRenderer);
        NetworkBoostersIntegration boostersIntegration = resolveNetworkBoosters(snapshot);
        AwardCoordinator awardCoordinator = new AwardCoordinator(
            economy,
            new BukkitAwardPrepareEventDispatcher(this.plugin, this.plugin.getLogger()),
            boostersIntegration.awardCalculator(),
            this::activeConfig
        );
        PlayerLifecycleCoordinator lifecycleCoordinator = new PlayerLifecycleCoordinator(
            store,
            persistence.playerNames()::updateCurrentMapping,
            playerSettings::isReady,
            boostersIntegration.service().map(service -> new PlayerLifecycleCoordinator.PlayerBoostersReadiness() {
                @Override
                public java.util.concurrent.CompletableFuture<Void> load(java.util.UUID playerId) {
                    return service.load(playerId).thenApply(snapshot -> null);
                }

                @Override
                public boolean isReady(java.util.UUID playerId) {
                    return service.isReady(playerId);
                }
            }),
            this.inFlightTracker,
            this::runOnMainThread,
            this::scheduleDelayed,
            this.plugin.getLogger(),
            Clock.systemUTC()
        );
        ProgressPointsService service = new ProgressPointsService(
            store,
            economy,
            awardCoordinator,
            this.inFlightTracker,
            lifecycleCoordinator,
            this::activeConfig
        );

        if (!this.balanceStore.compareAndSet(null, store)) {
            store.close();
            throw new IllegalStateException("ProgressEngine balance store was already initialized");
        }
        if (!this.accountEconomy.compareAndSet(null, economy)) {
            store.close();
            throw new IllegalStateException("ProgressEngine account economy was already initialized");
        }
        if (!this.playerLifecycle.compareAndSet(null, lifecycleCoordinator)) {
            lifecycleCoordinator.close();
            store.close();
            throw new IllegalStateException("ProgressEngine player lifecycle was already initialized");
        }
        if (!this.pointsService.compareAndSet(null, service)) {
            lifecycleCoordinator.close();
            store.close();
            throw new IllegalStateException("ProgressEngine points service was already initialized");
        }
        if (!this.localizedMessages.compareAndSet(null, localizedMessages)) {
            lifecycleCoordinator.close();
            store.close();
            throw new IllegalStateException("ProgressEngine localization was already initialized");
        }
        if (!this.identityRenderer.compareAndSet(null, identityRenderer)) {
            identityRenderer.close();
            lifecycleCoordinator.close();
            store.close();
            throw new IllegalStateException("ProgressEngine identity renderer was already initialized");
        }
        if (!this.feedbackService.compareAndSet(null, feedbackService)) {
            feedbackService.close();
            identityRenderer.close();
            lifecycleCoordinator.close();
            store.close();
            throw new IllegalStateException("ProgressEngine feedback service was already initialized");
        }
        this.resources.register("balance-store", store);
        this.resources.register("player-lifecycle", lifecycleCoordinator);
        this.resources.register("identity-renderer", identityRenderer);
        this.resources.register("feedback-service", feedbackService);
        this.resources.register("award-feedback-coalescer", awardFeedbackCoalescer);
        this.plugin.getServer().getPluginManager().registerEvents(identityInvalidationListener, this.plugin);
        this.resources.register("identity-invalidation-listener", () -> HandlerList.unregisterAll(identityInvalidationListener));
        this.plugin.getServer().getPluginManager().registerEvents(feedbackService, this.plugin);
        this.resources.register("feedback-listener", () -> HandlerList.unregisterAll(feedbackService));
        this.plugin.getServer().getPluginManager().registerEvents(awardFeedbackListener, this.plugin);
        this.resources.register("award-feedback-listener", () -> HandlerList.unregisterAll(awardFeedbackListener));
        BukkitPlayerLifecycleListener listener = new BukkitPlayerLifecycleListener(lifecycleCoordinator, playerSettings);
        this.plugin.getServer().getPluginManager().registerEvents(listener, this.plugin);
        this.resources.register("player-lifecycle-listener", () -> HandlerList.unregisterAll(listener));
        processAlreadyOnlinePlayers(playerSettings, lifecycleCoordinator);
        if (this.lifecycle.state() == RuntimeState.STARTING) {
            this.lifecycle.transitionTo(RuntimeState.READY);
        }
        this.plugin.getLogger().info("ProgressEngine economic runtime initialized. Public service remains hidden until later blocks are complete.");
    }

    private PlayerSettingsService resolvePlayerSettingsService() {
        return resolveService(PlayerSettingsService.class, "NetworkPlayerSettings PlayerSettingsService");
    }

    private <T> T resolveService(Class<T> type, String displayName) {
        RegisteredServiceProvider<T> registration = this.plugin.getServer().getServicesManager().getRegistration(type);
        if (registration == null) {
            throw new IllegalStateException(displayName + " is not registered");
        }
        return registration.getProvider();
    }

    private NetworkBoostersIntegration resolveNetworkBoosters(ConfigurationSnapshot snapshot) {
        return NetworkBoostersIntegrationFactory.resolve(this.plugin, snapshot.config().integrations().networkBoostersEnabled());
    }

    private void processAlreadyOnlinePlayers(PlayerSettingsService playerSettings, PlayerLifecycleCoordinator lifecycleCoordinator) {
        for (org.bukkit.entity.Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline() || !playerSettings.isReady(player.getUniqueId())) {
                continue;
            }
            lifecycleCoordinator.startSession(player.getUniqueId(), player.getName(), player::isOnline);
        }
    }

    private void runOnMainThread(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        try {
            this.plugin.getServer().getScheduler().runTask(this.plugin, task);
        } catch (RuntimeException exception) {
            this.plugin.getLogger().log(Level.WARNING, "ProgressEngine could not schedule lifecycle work during shutdown", exception);
        }
    }

    private AutoCloseable scheduleDelayed(Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        org.bukkit.scheduler.BukkitTask scheduled = this.plugin.getServer().getScheduler()
            .runTaskLater(this.plugin, task, delayTicks);
        return scheduled::cancel;
    }

    private ProgressEngineConfig activeConfig() {
        return this.configurationManager.activeSnapshot()
            .map(ConfigurationSnapshot::config)
            .orElseThrow(() -> new IllegalStateException("ProgressEngine configuration is not initialized"));
    }

    private ConfigurationSnapshot activeSnapshot() {
        return this.configurationManager.activeSnapshot()
            .orElseThrow(() -> new IllegalStateException("ProgressEngine configuration is not initialized"));
    }

    private void transitionDatabaseUnavailable() {
        RuntimeState state = this.lifecycle.state();
        if (state != RuntimeState.SHUTTING_DOWN && state != RuntimeState.CLOSED && state != RuntimeState.UNAVAILABLE_DATABASE) {
            this.lifecycle.transitionTo(RuntimeState.UNAVAILABLE_DATABASE);
        }
    }

    private void disableOnMainThread() {
        try {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                if (this.plugin.isEnabled()) {
                    this.plugin.getServer().getPluginManager().disablePlugin(this.plugin);
                }
            });
        } catch (RuntimeException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "ProgressEngine could not schedule plugin disable after startup failure", exception);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
