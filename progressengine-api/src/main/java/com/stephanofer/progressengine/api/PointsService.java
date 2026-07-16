package com.stephanofer.progressengine.api;

import org.bukkit.plugin.Plugin;

/**
 * Entry point registered in Bukkit ServicesManager for ProgressEngine consumers.
 * <p>
 * The service is published only after ProgressEngine has loaded configuration,
 * completed database migrations and initialized its runtime. Consumers should
 * resolve it from ServicesManager at use time or observe Bukkit's
 * ServiceRegisterEvent if their own startup can happen before this asynchronous
 * initialization completes.
 */
public interface PointsService {
    /**
     * Creates a client bound to the consuming plugin for source attribution.
     *
     * @param plugin the consuming plugin
     * @return a plugin-bound points client
     */
    PointsClient client(Plugin plugin);
}
