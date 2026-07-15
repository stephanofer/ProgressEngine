package com.stephanofer.progressengine.api.operation;

/**
 * Stable namespaced reason that explains why an economic movement exists.
 */
public record OperationReason(String value) {
    /** Maximum allowed reason length. */
    public static final int MAX_LENGTH = 128;
    private static final String NAMESPACE_PATTERN = "[a-z0-9._-]+";
    private static final String PATH_PATTERN = "[a-z0-9._/-]+";

    /**
     * Creates a reason.
     *
     * @param value the namespaced reason, for example {@code skywars:kill}
     */
    public OperationReason {
        if (value == null) {
            throw new NullPointerException("value cannot be null");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("value exceeds " + MAX_LENGTH + " characters");
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            throw new IllegalArgumentException("value must use namespace:path format");
        }
        String namespace = value.substring(0, separator);
        String path = value.substring(separator + 1);
        if (!namespace.matches(NAMESPACE_PATTERN) || !path.matches(PATH_PATTERN)) {
            throw new IllegalArgumentException("value contains invalid characters");
        }
    }

    /**
     * Creates a reason from a namespaced string.
     *
     * @param value the namespaced reason
     * @return the validated reason
     */
    public static OperationReason of(String value) {
        return new OperationReason(value);
    }

    @Override
    public String toString() {
        return this.value;
    }
}
