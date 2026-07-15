package com.stephanofer.progressengine;

import com.stephanofer.progressengine.config.BoostedYamlConfigurationLoader;
import com.stephanofer.progressengine.config.ConfigurationManager;
import com.stephanofer.progressengine.config.ConfigurationProblem;
import com.stephanofer.progressengine.config.ConfigurationReloadResult;
import com.stephanofer.progressengine.lifecycle.InFlightTracker;
import com.stephanofer.progressengine.lifecycle.LifecycleResources;
import com.stephanofer.progressengine.lifecycle.RuntimeLifecycle;
import com.stephanofer.progressengine.lifecycle.RuntimeState;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProgressEngineRuntime implements AutoCloseable {
    private final JavaPlugin plugin;
    private final RuntimeLifecycle lifecycle;
    private final InFlightTracker inFlightTracker;
    private final LifecycleResources resources;
    private final ConfigurationManager configurationManager;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();

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
            new BoostedYamlConfigurationLoader(plugin.getDataFolder().toPath(), () -> plugin.getResource("config.yml"), Clock.systemUTC()),
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
            this.plugin.getLogger().info("ProgressEngine configuration loaded. Runtime remains STARTING until infrastructure is initialized.");
        });
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
