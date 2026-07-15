package com.stephanofer.progressengine.lifecycle;

public enum RuntimeState {
    STARTING,
    READY,
    DEGRADED_REDIS,
    UNAVAILABLE_DATABASE,
    SHUTTING_DOWN,
    CLOSED
}
