package com.levelsfr.inspectmc.client;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** Client-side implementation behind the dump result open buttons. */
public final class DumpOpenUtil {
    private static final int MAX_TOKEN_LENGTH = 4_096;

    private DumpOpenUtil() {
    }

    public static String command(String target, Path path) {
        String normalized = path.toAbsolutePath().normalize().toString();
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
        return "/inspectmc-open " + target + " " + token;
    }

    public static OpenResult open(String token, boolean directory) {
        if (token.length() > MAX_TOKEN_LENGTH) {
            return OpenResult.failure("The encoded dump path is too long.");
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            if (decoded.indexOf('\0') >= 0) {
                return OpenResult.failure("Invalid dump path.");
            }

            Path path = Path.of(decoded).toAbsolutePath().normalize();
            Path dumpDirectory = directory ? path : path.getParent();
            if (!isDumpDirectory(dumpDirectory)) {
                return OpenResult.failure("Only the InspectMC dump folder can be opened.");
            }
            if (directory && !Files.isDirectory(path)) {
                return OpenResult.failure("The InspectMC dump folder no longer exists.");
            }
            if (!directory && (!Files.isRegularFile(path) || !isDumpFile(path))) {
                return OpenResult.failure("The InspectMC dump file no longer exists or is not supported.");
            }

            openWithSystem(path);
            return OpenResult.success(path);
        } catch (IllegalArgumentException exception) {
            return OpenResult.failure("Invalid InspectMC dump path.");
        } catch (SecurityException exception) {
            return OpenResult.failure("The operating system blocked the local open request.");
        } catch (IOException exception) {
            return OpenResult.failure("Could not launch the default application for this dump.");
        }
    }

    private static boolean isDumpDirectory(Path path) {
        for (Path current = path; current != null; current = current.getParent()) {
            Path parent = current.getParent();
            if (parent != null && current.getFileName() != null && parent.getFileName() != null
                    && current.getFileName().toString().equalsIgnoreCase("dumps")
                    && parent.getFileName().toString().equalsIgnoreCase("inspectmc")) {
                return true;
            }
        }
        return false;
    }

    private static void openWithSystem(Path path) throws IOException {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(path.toFile());
                    return;
                }
            }
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            // Use the operating system launcher when Java Desktop cannot open the file.
        }

        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command = operatingSystem.startsWith("windows")
                ? List.of("rundll32.exe", "url.dll,FileProtocolHandler", path.toString())
                : operatingSystem.contains("mac")
                ? List.of("open", path.toString())
                : List.of("xdg-open", path.toString());
        new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static boolean isDumpFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".json") || name.endsWith(".csv")
                || name.endsWith(".xlsx") || name.endsWith(".zip");
    }

    public record OpenResult(boolean success, String message, Path path) {
        private static OpenResult success(Path path) {
            return new OpenResult(true, "Opened " + path.getFileName(), path);
        }

        private static OpenResult failure(String message) {
            return new OpenResult(false, message, null);
        }
    }
}
