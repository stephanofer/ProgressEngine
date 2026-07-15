package com.stephanofer.progressengine;

import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.Source;

public final class ProgressEngine extends JavaPlugin {
    private final PaperCommandManager.Bootstrapped<Source> commandManager;
    private ProgressEngineRuntime runtime;

    public ProgressEngine(PaperCommandManager.Bootstrapped<Source> commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public void onEnable() {
        this.commandManager.onEnable();
        this.runtime = ProgressEngineRuntime.create(this);
        this.runtime.start();
    }

    @Override
    public void onDisable() {
        if (this.runtime != null) {
            this.runtime.shutdown();
            this.runtime = null;
        }
    }
}
