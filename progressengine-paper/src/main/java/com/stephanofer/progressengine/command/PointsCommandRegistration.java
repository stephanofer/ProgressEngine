package com.stephanofer.progressengine.command;

import com.stephanofer.progressengine.config.CommandSettings;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

public final class PointsCommandRegistration {
    private PointsCommandRegistration() {
    }

    public static void register(PaperCommandManager.Bootstrapped<Source> manager,
                                CommandSettings.Registration registration,
                                PointsCommandExecutor executor) {
        String root = registration.root();
        String[] aliases = registration.aliases().toArray(String[]::new);
        manager.command(manager.commandBuilder(root, aliases)
            .commandDescription(Description.of("Show your points balance"))
            .handler(context -> executor.execute(PointsCommandRoute.BALANCE_SELF, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("balance")
            .optional("target", StringParser.stringParser(), playerSuggestions(executor))
            .handler(context -> executor.execute(PointsCommandRoute.BALANCE, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("pay")
            .required("target", StringParser.stringParser(), playerSuggestions(executor))
            .required("amount", StringParser.stringParser())
            .handler(context -> executor.execute(PointsCommandRoute.PAY, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("pay").literal("confirm")
            .required("token", StringParser.stringParser())
            .handler(context -> executor.execute(PointsCommandRoute.PAY_CONFIRM, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("pay").literal("retry")
            .required("token", StringParser.stringParser())
            .handler(context -> executor.execute(PointsCommandRoute.PAY_RETRY, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("history")
            .optional("page", StringParser.stringParser())
            .handler(context -> executor.execute(PointsCommandRoute.HISTORY_SELF, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("help")
            .handler(context -> executor.execute(PointsCommandRoute.HELP, context)));

        manager.command(manager.commandBuilder(root, aliases).literal("admin").literal("add")
            .required("target", StringParser.stringParser(), playerSuggestions(executor))
            .required("amount", StringParser.stringParser())
            .optional("reason", StringParser.stringParser())
            .handler(context -> executor.execute(PointsCommandRoute.ADMIN_ADD, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("admin").literal("remove")
            .required("target", StringParser.stringParser(), playerSuggestions(executor))
            .required("amount", StringParser.stringParser())
            .optional("reason", StringParser.stringParser())
            .handler(context -> executor.execute(PointsCommandRoute.ADMIN_REMOVE, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("admin").literal("set")
            .required("target", StringParser.stringParser(), playerSuggestions(executor))
            .required("amount", StringParser.stringParser())
            .optional("reason", StringParser.stringParser())
            .handler(context -> executor.execute(PointsCommandRoute.ADMIN_SET, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("admin").literal("reset")
            .required("target", StringParser.stringParser(), playerSuggestions(executor))
            .optional("reason", StringParser.stringParser())
            .handler(context -> executor.execute(PointsCommandRoute.ADMIN_RESET, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("admin").literal("retry")
            .required("token", StringParser.stringParser())
            .handler(context -> executor.execute(PointsCommandRoute.ADMIN_RETRY, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("admin").literal("history")
            .required("target", StringParser.stringParser(), playerSuggestions(executor))
            .optional("page", StringParser.stringParser())
            .handler(context -> executor.execute(PointsCommandRoute.ADMIN_HISTORY, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("admin").literal("reload")
            .handler(context -> executor.execute(PointsCommandRoute.ADMIN_RELOAD, context)));
        manager.command(manager.commandBuilder(root, aliases).literal("admin").literal("status")
            .handler(context -> executor.execute(PointsCommandRoute.ADMIN_STATUS, context)));
    }

    private static SuggestionProvider<Source> playerSuggestions(PointsCommandExecutor executor) {
        return SuggestionProvider.blockingStrings((context, input) -> executor.suggestPlayers(input.lastRemainingToken()));
    }
}
