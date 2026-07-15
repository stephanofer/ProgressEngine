package com.stephanofer.progressengine.lifecycle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LifecycleResources implements AutoCloseable {
    private final Deque<RegisteredResource> resources = new ArrayDeque<>();
    private final Set<String> names = new HashSet<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public synchronized void register(String name, AutoCloseable resource) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(resource, "resource");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (this.closed.get()) {
            throw new IllegalStateException("Lifecycle resources are closed");
        }
        if (!this.names.add(name)) {
            throw new IllegalArgumentException("Resource already registered: " + name);
        }
        this.resources.addLast(new RegisteredResource(name, resource));
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        RuntimeException failure = null;
        while (true) {
            RegisteredResource resource;
            synchronized (this) {
                resource = this.resources.pollLast();
            }
            if (resource == null) {
                break;
            }
            try {
                resource.resource().close();
            } catch (Exception exception) {
                RuntimeException wrapped = new RuntimeException("Failed to close resource " + resource.name(), exception);
                if (failure == null) {
                    failure = wrapped;
                } else {
                    failure.addSuppressed(wrapped);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private record RegisteredResource(String name, AutoCloseable resource) {
    }
}
