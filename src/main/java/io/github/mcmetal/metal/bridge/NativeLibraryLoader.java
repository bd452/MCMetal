package io.github.mcmetal.metal.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NativeLibraryLoader {
    private static final String BUNDLED_LIBRARY_PATH = "/natives/macos/libminecraft_metal.dylib";
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private NativeLibraryLoader() {
    }

    public static void load() {
        if (LOADED.get()) {
            return;
        }

        synchronized (NativeLibraryLoader.class) {
            if (LOADED.get()) {
                return;
            }
            loadInternal();
            LOADED.set(true);
        }
    }

    private static void loadInternal() {
        UnsatisfiedLinkError systemLoadError;
        try {
            System.loadLibrary(NativeApi.LIBRARY_BASE_NAME);
            return;
        } catch (UnsatisfiedLinkError unsatisfiedLinkError) {
            systemLoadError = unsatisfiedLinkError;
        }

        try {
            loadBundledLibrary();
        } catch (IOException | UnsatisfiedLinkError loadError) {
            NativeBridgeException exception = new NativeBridgeException(
                "Unable to load native library '" + NativeApi.LIBRARY_BASE_NAME
                    + "' from system path or bundled resource.",
                loadError
            );
            exception.addSuppressed(systemLoadError);
            throw exception;
        }
    }

    private static void loadBundledLibrary() throws IOException {
        try (InputStream inputStream = NativeLibraryLoader.class.getResourceAsStream(BUNDLED_LIBRARY_PATH)) {
            if (inputStream == null) {
                throw new IOException("Bundled native library not found at " + BUNDLED_LIBRARY_PATH);
            }

            Path extractedLibrary = Files.createTempFile("mcmetal-", ".dylib");
            extractedLibrary.toFile().deleteOnExit();
            Files.copy(inputStream, extractedLibrary, StandardCopyOption.REPLACE_EXISTING);
            System.load(extractedLibrary.toAbsolutePath().toString());
        }
    }
}
