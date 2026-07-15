package com.stephanofer.progressengine.config;

import java.io.IOException;

public interface ConfigurationLoader {
    LoadedConfiguration load(long revision) throws ConfigurationLoadException;

    void persist(LoadedConfiguration configuration) throws IOException;
}
