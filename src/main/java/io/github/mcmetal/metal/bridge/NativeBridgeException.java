package io.github.mcmetal.metal.bridge;

public final class NativeBridgeException extends RuntimeException {
    public NativeBridgeException(String message) {
        super(message);
    }

    public NativeBridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
