package com.stephanofer.progressengine.command;

import com.stephanofer.progressengine.config.CommandSettings;

public enum PointsCommandRoute {
    BALANCE_SELF(CommandSettings.CommandPermission.BALANCE),
    BALANCE(CommandSettings.CommandPermission.BALANCE),
    PAY(CommandSettings.CommandPermission.PAY),
    PAY_CONFIRM(CommandSettings.CommandPermission.PAY),
    PAY_RETRY(CommandSettings.CommandPermission.PAY),
    HISTORY_SELF(CommandSettings.CommandPermission.HISTORY),
    HELP(CommandSettings.CommandPermission.HELP),
    ADMIN_ADD(CommandSettings.CommandPermission.ADMIN_ADD),
    ADMIN_REMOVE(CommandSettings.CommandPermission.ADMIN_REMOVE),
    ADMIN_SET(CommandSettings.CommandPermission.ADMIN_SET),
    ADMIN_RESET(CommandSettings.CommandPermission.ADMIN_RESET),
    ADMIN_RETRY(CommandSettings.CommandPermission.ADMIN_ADD),
    ADMIN_HISTORY(CommandSettings.CommandPermission.ADMIN_HISTORY),
    ADMIN_RELOAD(CommandSettings.CommandPermission.ADMIN_RELOAD),
    ADMIN_STATUS(CommandSettings.CommandPermission.ADMIN_STATUS);

    private final CommandSettings.CommandPermission permission;

    PointsCommandRoute(CommandSettings.CommandPermission permission) {
        this.permission = permission;
    }

    public CommandSettings.CommandPermission permission() {
        return this.permission;
    }
}
