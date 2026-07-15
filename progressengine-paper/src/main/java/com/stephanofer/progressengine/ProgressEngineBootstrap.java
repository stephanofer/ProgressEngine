package com.stephanofer.progressengine;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.setting.ManagerSetting;

@SuppressWarnings("UnstableApiUsage")
public final class ProgressEngineBootstrap implements PluginBootstrap {
    private PaperCommandManager.Bootstrapped<Source> commandManager;

    @Override
    public void bootstrap(BootstrapContext context) {
        this.commandManager = PaperCommandManager.builder(PaperSimpleSenderMapper.simpleSenderMapper())
            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
            .buildBootstrapped(context);
        this.commandManager.settings().set(ManagerSetting.ALLOW_UNSAFE_REGISTRATION, true);
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        if (this.commandManager == null) {
            throw new IllegalStateException("Cloud command manager was not initialized during bootstrap");
        }
        return new ProgressEngine(this.commandManager);
    }
}
