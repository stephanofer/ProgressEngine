package com.stephanofer.progressengine.lifecycle;

public record InFlightCounts(int loads, int mutations) {
    public int total() {
        return this.loads + this.mutations;
    }
}
