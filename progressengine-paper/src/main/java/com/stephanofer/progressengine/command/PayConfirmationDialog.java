package com.stephanofer.progressengine.command;

import io.papermc.paper.dialog.Dialog;
import com.stephanofer.progressengine.config.PayConfirmationDialogSettings;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

final class PayConfirmationDialog {
    private PayConfirmationDialog() {
    }

    static void show(Player player, PayConfirmationDialogSettings settings, String language, Component receiver, Component amount, String exactAmount, String balanceAfter,
                      long expirySeconds, Runnable confirm) {
        PayConfirmationDialogSettings.Locale locale = settings.locales().getOrDefault(language, settings.locales().get("en"));
        Component title = Component.text(resolveText(locale.title(), exactAmount, balanceAfter, expirySeconds), NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
        Component body = Component.join(JoinConfiguration.newlines(), locale.body().stream()
            .map(line -> bodyLine(line, receiver, amount, exactAmount, balanceAfter, expirySeconds))
            .toList());
        ClickCallback.Options options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(expirySeconds)).build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
            .type(DialogType.confirmation(
                ActionButton.create(Component.text(resolveText(locale.confirmLabel(), exactAmount, balanceAfter, expirySeconds), NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    Component.text(resolveText(locale.confirmTooltip(), exactAmount, balanceAfter, expirySeconds), NamedTextColor.GRAY), settings.confirmButtonWidth(),
                    DialogAction.customClick((view, audience) -> confirm.run(), options)),
                ActionButton.create(Component.text(resolveText(locale.cancelLabel(), exactAmount, balanceAfter, expirySeconds), NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    Component.text(resolveText(locale.cancelTooltip(), exactAmount, balanceAfter, expirySeconds), NamedTextColor.GRAY), settings.cancelButtonWidth(),
                    DialogAction.customClick((view, audience) -> {}, ClickCallback.Options.builder().uses(1).build()))
            ))
            .base(DialogBase.create(title, Component.text(resolveText(locale.externalTitle(), exactAmount, balanceAfter, expirySeconds)), settings.canCloseWithEscape(), settings.pause(),
                DialogBase.DialogAfterAction.CLOSE, List.of(DialogBody.plainMessage(body, settings.bodyWidth())), List.of())));
        player.showDialog(dialog);
    }

    private static Component bodyLine(String line, Component receiver, Component amount, String exactAmount, String balanceAfter, long expirySeconds) {
        if (line.equals("<receiver>")) {
            return receiver.decorate(TextDecoration.BOLD);
        }
        if (line.equals("<amount>")) {
            return amount.decorate(TextDecoration.BOLD);
        }
        return Component.text(resolveText(line, exactAmount, balanceAfter, expirySeconds), NamedTextColor.GRAY);
    }

    private static String resolveText(String text, String exactAmount, String balanceAfter, long expirySeconds) {
        return text
            .replace("<amount_exact>", exactAmount)
            .replace("<balance_after>", balanceAfter)
            .replace("<expires_in>", Long.toString(expirySeconds));
    }
}
