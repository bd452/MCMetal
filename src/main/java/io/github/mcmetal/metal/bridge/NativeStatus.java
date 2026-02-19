package io.github.mcmetal.metal.bridge;

public final class NativeStatus {
    public static final int OK = 0;
    public static final int ALREADY_INITIALIZED = 1;
    public static final int INVALID_ARGUMENT = 2;
    public static final int INITIALIZATION_FAILED = 3;

    private NativeStatus() {
    }

    public static boolean isSuccess(int statusCode) {
        return statusCode == OK || statusCode == ALREADY_INITIALIZED;
    }

    public static String describe(int statusCode) {
        return switch (statusCode) {
            case OK -> "OK";
            case ALREADY_INITIALIZED -> "ALREADY_INITIALIZED";
            case INVALID_ARGUMENT -> "INVALID_ARGUMENT";
            case INITIALIZATION_FAILED -> "INITIALIZATION_FAILED";
            default -> "UNKNOWN";
        };
    }
}
