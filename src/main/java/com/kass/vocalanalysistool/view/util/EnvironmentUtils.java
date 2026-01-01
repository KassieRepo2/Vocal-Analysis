package com.kass.vocalanalysistool.view.util;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for checking the Python environment state.
 */
public final class EnvironmentUtils {

    private EnvironmentUtils() {}

    /**
     * Returns true if the Python environment is already set up.
     */
    public static boolean isEnvironmentReady() {
        try {
            Path dataDir = getDataDir();
            Path venvPy = dataDir.resolve(".venv")
                                 .resolve("Scripts")
                                 .resolve("python.exe");
            Path readyMark = dataDir.resolve(".venv")
                                    .resolve(".ready");

            return Files.exists(venvPy) && Files.exists(readyMark);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the writable data directory used by the app.
     */
    private static Path getDataDir() {
        String jpackagePath = System.getProperty("jpackage.app-path");

        if (jpackagePath != null && !jpackagePath.isBlank()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = (localAppData != null && !localAppData.isBlank())
                    ? Path.of(localAppData)
                    : Path.of(System.getProperty("user.home"));
            return base.resolve("VocalAnalysisTool").toAbsolutePath();
        }

        // IntelliJ / dev mode
        return Path.of(System.getProperty("user.dir")).toAbsolutePath();
    }
}
