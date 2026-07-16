package com.stephanofer.progressengine.command;

import java.util.List;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;

public interface PointsCommandExecutor {
    void execute(PointsCommandRoute route, CommandContext<Source> context);

    default List<String> suggestPlayers(String input) {
        return List.of();
    }
}
