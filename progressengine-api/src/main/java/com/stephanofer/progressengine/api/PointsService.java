package com.stephanofer.progressengine.api;

import org.bukkit.plugin.Plugin;

/**
 * Entry point registered in Bukkit ServicesManager for ProgressEngine consumers.
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
