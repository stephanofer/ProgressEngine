package com.stephanofer.progressengine.api.source;

import com.stephanofer.progressengine.api.internal.ApiValidation;

/**
 * Runtime source that originated an operation.
 */
public record OperationSource(String pluginName, String serverId) {

    /**
     * Creates an operation source.
     *
     * @param pluginName plugin that submitted the operation
     * @param serverId server that processed the operation
     */
    public OperationSource {
        pluginName = ApiValidation.requireText(pluginName, "pluginName", 64);
        serverId = ApiValidation.requireText(serverId, "serverId", 64);
    }
}
