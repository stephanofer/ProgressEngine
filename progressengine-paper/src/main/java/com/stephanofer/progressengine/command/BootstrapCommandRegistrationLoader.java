package com.stephanofer.progressengine.command;

import com.stephanofer.progressengine.config.CommandSettings;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BootstrapCommandRegistrationLoader {
    private static final String COMMANDS_FILE = "commands.yml";

    private BootstrapCommandRegistrationLoader() {
    }

    public static CommandSettings.Registration load(PluginProviderContext context) {
        CommandSettings.Registration defaults = CommandSettings.defaults().registration();
        try {
            byte[] defaultBytes = readDefaults();
            Path commandsFile = context.getDataDirectory().resolve(COMMANDS_FILE);
            byte[] documentBytes = Files.exists(commandsFile) ? Files.readAllBytes(commandsFile) : defaultBytes;
            YamlDocument yaml = YamlDocument.create(
                new ByteArrayInputStream(documentBytes),
                new ByteArrayInputStream(defaultBytes),
                GeneralSettings.builder().setUseDefaults(false).build(),
                LoaderSettings.builder().setCreateFileIfAbsent(false).setAutoUpdate(false).setAllowDuplicateKeys(false).build(),
                DumperSettings.DEFAULT,
                UpdaterSettings.builder().setAutoSave(false).setVersioning(new BasicVersioning("config-version")).build()
            );
            String root = string(yaml.get("registration.root", defaults.root()), defaults.root());
            return new CommandSettings.Registration(root, aliases(yaml.get("registration.aliases", defaults.aliases())));
        } catch (Exception exception) {
            context.getLogger().warn("ProgressEngine could not read command registration during bootstrap; using default /points layout.", exception);
            return defaults;
        }
    }

    private static byte[] readDefaults() throws IOException {
        try (InputStream stream = BootstrapCommandRegistrationLoader.class.getClassLoader().getResourceAsStream(COMMANDS_FILE)) {
            if (stream == null) {
                throw new IOException("missing bundled " + COMMANDS_FILE);
            }
            return stream.readAllBytes();
        }
    }

    private static String string(Object value, String fallback) {
        return value instanceof String text ? text : fallback;
    }

    private static List<String> aliases(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        List<String> aliases = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String alias) {
                aliases.add(alias);
            }
        }
        return aliases;
    }
}
