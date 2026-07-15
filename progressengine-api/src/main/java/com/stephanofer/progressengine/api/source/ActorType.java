package com.stephanofer.progressengine.api.source;

/**
 * Kind of actor that caused an economic operation.
 */
public enum ActorType {
    /** ProgressEngine or controlled internal automation. */
    SYSTEM,
    /** A plugin consumer. */
    PLUGIN,
    /** A player. */
    PLAYER,
    /** The server console. */
    CONSOLE
}
