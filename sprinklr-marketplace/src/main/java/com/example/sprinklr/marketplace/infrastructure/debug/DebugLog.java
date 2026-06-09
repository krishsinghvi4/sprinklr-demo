package com.example.sprinklr.marketplace.infrastructure.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class DebugLog {

    private static final Path LOG_PATH = Path.of(
            "/Users/krish.singhvi/Desktop/sprinklr-demo/.cursor/debug-c28fed.log"
    );

    private DebugLog() {}

    public static void write(String location, String message, String hypothesisId, String dataJson) {
        try {
            String line = String.format(
                    "{\"sessionId\":\"c28fed\",\"location\":\"%s\",\"message\":\"%s\",\"hypothesisId\":\"%s\",\"data\":%s,\"timestamp\":%d}%n",
                    escape(location),
                    escape(message),
                    escape(hypothesisId),
                    dataJson == null || dataJson.isBlank() ? "{}" : dataJson,
                    System.currentTimeMillis()
            );
            Files.writeString(LOG_PATH, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // debug-only
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
