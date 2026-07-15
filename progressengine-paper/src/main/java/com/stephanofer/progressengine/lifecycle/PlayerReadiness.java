package com.stephanofer.progressengine.lifecycle;

import java.util.UUID;

@FunctionalInterface
public interface PlayerReadiness {
    boolean isReady(UUID playerId);
}
