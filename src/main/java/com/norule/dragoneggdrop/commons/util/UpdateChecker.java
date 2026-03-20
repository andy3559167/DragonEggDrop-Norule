package com.norule.dragoneggdrop.commons.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lightweight update checker based on Spigot's legacy update endpoint.
 */
public final class UpdateChecker {

    private static final String RESOURCE_URL_FORMAT = "https://api.spigotmc.org/legacy/update.php?resource=%d";

    private static UpdateChecker instance;

    private final JavaPlugin plugin;
    private final int resourceId;

    private volatile UpdateResult lastResult;

    private UpdateChecker(@NotNull JavaPlugin plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    /**
     * Initialize the checker singleton.
     *
     * @param plugin plugin instance
     * @param resourceId Spigot resource id
     */
    public static void init(@NotNull JavaPlugin plugin, int resourceId) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must not be null");
        }

        instance = new UpdateChecker(plugin, resourceId);
    }

    /**
     * Get the checker instance.
     *
     * @return the update checker
     */
    @NotNull
    public static UpdateChecker get() {
        if (instance == null) {
            throw new IllegalStateException("UpdateChecker has not been initialized");
        }

        return instance;
    }

    /**
     * Request an async update check.
     *
     * @return future result
     */
    @NotNull
    public CompletableFuture<@NotNull UpdateResult> requestUpdateCheck() {
        return CompletableFuture.supplyAsync(this::checkNow);
    }

    /**
     * Get the most recent result.
     *
     * @return the last result, or null if none
     */
    @Nullable
    public UpdateResult getLastResult() {
        return this.lastResult;
    }

    @NotNull
    private UpdateResult checkNow() {
        String currentVersion = plugin.getDescription().getVersion();
        String latestVersion = requestLatestVersion();

        UpdateResult result;
        if (latestVersion == null || latestVersion.isEmpty()) {
            result = new UpdateResult(currentVersion, currentVersion, UpdateReason.CONNECTION_ERROR);
        } else {
            int comparison = compareVersions(currentVersion, latestVersion);
            if (comparison < 0) {
                result = new UpdateResult(currentVersion, latestVersion, UpdateReason.UPDATE_AVAILABLE);
            } else if (comparison > 0) {
                result = new UpdateResult(currentVersion, latestVersion, UpdateReason.UNRELEASED_VERSION);
            } else {
                result = new UpdateResult(currentVersion, latestVersion, UpdateReason.UP_TO_DATE);
            }
        }

        this.lastResult = result;
        return result;
    }

    @Nullable
    private String requestLatestVersion() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(String.format(RESOURCE_URL_FORMAT, resourceId));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setUseCaches(false);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return null;
            }

            InputStream responseStream = connection.getInputStream();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
                String response = reader.readLine();
                return response != null ? response.trim() : null;
            }
        } catch (IOException ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static int compareVersions(@Nullable String leftVersion, @Nullable String rightVersion) {
        List<String> left = tokenizeVersion(leftVersion);
        List<String> right = tokenizeVersion(rightVersion);
        int max = Math.max(left.size(), right.size());
        for (int i = 0; i < max; i++) {
            String leftToken = i < left.size() ? left.get(i) : null;
            String rightToken = i < right.size() ? right.get(i) : null;

            if (leftToken == null && rightToken == null) {
                continue;
            }

            if (leftToken == null) {
                if (isNumeric(rightToken)) {
                    long rightNumeric = parseLongSafe(rightToken);
                    if (rightNumeric == 0L) {
                        continue;
                    }

                    return -1;
                }

                return 1;
            }

            if (rightToken == null) {
                if (isNumeric(leftToken)) {
                    long leftNumeric = parseLongSafe(leftToken);
                    if (leftNumeric == 0L) {
                        continue;
                    }

                    return 1;
                }

                return -1;
            }

            boolean leftNumeric = isNumeric(leftToken);
            boolean rightNumeric = isNumeric(rightToken);

            if (leftNumeric && rightNumeric) {
                long leftNumericValue = parseLongSafe(leftToken);
                long rightNumericValue = parseLongSafe(rightToken);
                if (leftNumericValue != rightNumericValue) {
                    return Long.compare(leftNumericValue, rightNumericValue);
                }

                continue;
            }

            if (leftNumeric != rightNumeric) {
                return leftNumeric ? 1 : -1;
            }

            int lexical = leftToken.compareToIgnoreCase(rightToken);
            if (lexical != 0) {
                return lexical;
            }
        }

        return 0;
    }

    @NotNull
    private static List<String> tokenizeVersion(@Nullable String version) {
        List<String> tokens = new ArrayList<>();
        if (version == null) {
            return tokens;
        }

        String[] split = version.trim().split("[^0-9A-Za-z]+");
        for (String token : split) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    private static boolean isNumeric(@Nullable String value) {
        return value != null && value.matches("\\d+");
    }

    private static long parseLongSafe(@NotNull String token) {
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException ignored) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * A reason describing the update check status.
     */
    public enum UpdateReason {

        UPDATE_AVAILABLE,
        UP_TO_DATE,
        UNRELEASED_VERSION,
        CONNECTION_ERROR

    }

    /**
     * Immutable update-check result.
     */
    public static final class UpdateResult {

        private final String currentVersion;
        private final String newestVersion;
        private final UpdateReason reason;

        private UpdateResult(@NotNull String currentVersion, @NotNull String newestVersion, @NotNull UpdateReason reason) {
            this.currentVersion = currentVersion;
            this.newestVersion = newestVersion;
            this.reason = reason;
        }

        /**
         * Check whether an update is available.
         *
         * @return true if update is available
         */
        public boolean requiresUpdate() {
            return reason == UpdateReason.UPDATE_AVAILABLE;
        }

        /**
         * Get the currently running plugin version.
         *
         * @return current version
         */
        @NotNull
        public String getCurrentVersion() {
            return currentVersion;
        }

        /**
         * Get the newest remote version.
         *
         * @return newest version
         */
        @NotNull
        public String getNewestVersion() {
            return newestVersion;
        }

        /**
         * Get the update-check result reason.
         *
         * @return reason
         */
        @NotNull
        public UpdateReason getReason() {
            return reason;
        }

    }

}
