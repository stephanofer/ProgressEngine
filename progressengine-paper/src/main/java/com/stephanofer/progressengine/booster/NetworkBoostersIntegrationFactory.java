package com.stephanofer.progressengine.booster;

import com.stephanofer.networkboosters.api.NetworkBoostersService;
import java.util.Optional;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class NetworkBoostersIntegrationFactory {
    private NetworkBoostersIntegrationFactory() {
    }

    public static AwardBoosterCalculator create(JavaPlugin plugin, boolean enabled) {
        return resolve(plugin, enabled).awardCalculator();
    }

    public static NetworkBoostersIntegration resolve(JavaPlugin plugin, boolean enabled) {
        if (!enabled) {
            return new NetworkBoostersIntegration(
                NetworkBoostersIntegration.Status.DISABLED,
                Optional.empty(),
                AwardBoosterCalculator.disabled()
            );
        }
        Plugin boostersPlugin = plugin.getServer().getPluginManager().getPlugin("NetworkBoosters");
        if (boostersPlugin == null || !boostersPlugin.isEnabled()) {
            return new NetworkBoostersIntegration(
                NetworkBoostersIntegration.Status.ABSENT,
                Optional.empty(),
                AwardBoosterCalculator.disabled()
            );
        }
        RegisteredServiceProvider<NetworkBoostersService> registration = plugin.getServer().getServicesManager()
            .getRegistration(NetworkBoostersService.class);
        if (registration == null) {
            throw new IllegalStateException("NetworkBoosters is enabled but its public service is not registered");
        }
        NetworkBoostersService service = registration.getProvider();
        return new NetworkBoostersIntegration(
            NetworkBoostersIntegration.Status.AVAILABLE,
            Optional.of(service),
            new NetworkBoostersAwardCalculator(service)
        );
    }
}
