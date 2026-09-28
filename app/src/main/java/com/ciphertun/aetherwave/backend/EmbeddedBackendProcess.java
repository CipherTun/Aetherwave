package com.ciphertun.aetherwave.backend;

import java.io.File;
import java.io.IOException;

/** Small Java bridge around Android's process launcher for the embedded Go server. */
public final class EmbeddedBackendProcess {
    private EmbeddedBackendProcess() {}

    public static Process start(File binary, String address) throws IOException {
        return new ProcessBuilder(
                binary.getAbsolutePath(),
                "-addr",
                address
        )
                .directory(binary.getParentFile())
                .redirectErrorStream(true)
                .start();
    }
}
