package com.stephanofer.progressengine.booster;

import com.stephanofer.networkboosters.api.NetworkBoostersService;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class NetworkBoostersIntegrationFactory {
    private NetworkBoostersIntegrationFactory() {
    }

    public static AwardBoosterCalculator create(JavaPlugin plugin, boolean enabled) {
        if (!enabled) {
            return AwardBoosterCalculator.disabled();
        }
        Plugin boostersPlugin = plugin.getServer().getPluginManager().getPlugin("NetworkBoosters");
        if (boostersPlugin == null || !boostersPlugin.isEnabled()) {
            return AwardBoosterCalculator.disabled();
        }
        RegisteredServiceProvider<NetworkBoostersService> registration = plugin.getServer().getServicesManager()
            .getRegistration(NetworkBoostersService.class);
        if (registration == null) {
            return AwardBoosterCalculator.unavailable("NetworkBoosters is enabled but its public service is not registered");
        }
        return new NetworkBoostersAwardCalculator(registration.getProvider());
    }
}
