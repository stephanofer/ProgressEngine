package com.stephanofer.progressengine;

import com.stephanofer.progressengine.command.BootstrapCommandRegistrationLoader;
import com.stephanofer.progressengine.command.PointsCommandRegistration;
import com.stephanofer.progressengine.command.ProgressEngineCommandBridge;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.Source;

@SuppressWarnings("UnstableApiUsage")
public final class ProgressEngineBootstrap implements PluginBootstrap {
    private PaperCommandManager.Bootstrapped<Source> commandManager;
    private ProgressEngineCommandBridge commandBridge;

    @Override
    public void bootstrap(BootstrapContext context) {
        this.commandBridge = new ProgressEngineCommandBridge();
        this.commandManager = PaperCommandManager.builder(PaperSimpleSenderMapper.simpleSenderMapper())
            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
            .buildBootstrapped(context);
        PointsCommandRegistration.register(
            this.commandManager,
            BootstrapCommandRegistrationLoader.load(context),
            this.commandBridge
        );
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        if (this.commandManager == null) {
            throw new IllegalStateException("Cloud command manager was not initialized during bootstrap");
        }
        if (this.commandBridge == null) {
            throw new IllegalStateException("ProgressEngine command bridge was not initialized during bootstrap");
        }
        return new ProgressEngine(this.commandManager, this.commandBridge);
    }
}
