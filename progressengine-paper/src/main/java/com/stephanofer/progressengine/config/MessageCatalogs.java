package com.stephanofer.progressengine.config;

import java.util.Map;
import java.util.Objects;

public record MessageCatalogs(Map<String, MessageCatalog> catalogs) {
    public MessageCatalogs {
        Objects.requireNonNull(catalogs, "catalogs");
        catalogs = Map.copyOf(catalogs);
        if (!catalogs.containsKey("es") || !catalogs.containsKey("en")) {
            throw new IllegalArgumentException("catalogs must contain es and en");
        }
    }

    public MessageCatalog require(String language) {
        MessageCatalog catalog = this.catalogs.get(language);
        if (catalog == null) {
            throw new IllegalArgumentException("unknown language: " + language);
        }
        return catalog;
    }
}
