package com.stephanofer.progressengine.command;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;

public final class ProgressEngineCommandBridge implements PointsCommandExecutor {
    private final AtomicReference<PointsCommandExecutor> delegate = new AtomicReference<>();

    public void set(PointsCommandExecutor executor) {
        this.delegate.set(Objects.requireNonNull(executor, "executor"));
    }

    public void clear() {
        this.delegate.set(null);
    }

    @Override
    public void execute(PointsCommandRoute route, CommandContext<Source> context) {
        PointsCommandExecutor executor = this.delegate.get();
        if (executor == null) {
            context.sender().source().sendMessage("ProgressEngine is still starting. Try again shortly.");
            return;
        }
        executor.execute(route, context);
    }

    @Override
    public List<String> suggestPlayers(String input) {
        PointsCommandExecutor executor = this.delegate.get();
        return executor == null ? List.of() : executor.suggestPlayers(input);
    }
}
