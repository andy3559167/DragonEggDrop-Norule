package com.norule.dragoneggdrop.commons.util;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common math and time helpers used by DragonEggDrop.
 */
public final class MathUtil {

    private static final Pattern TIME_TOKEN_PATTERN = Pattern.compile("(\\d+)\\s*([wdhms])", Pattern.CASE_INSENSITIVE);

    private MathUtil() { }

    /**
     * Clamp a double value into a range.
     *
     * @param value the input value
     * @param min the range minimum
     * @param max the range maximum
     *
     * @return clamped value
     */
    public static double clamp(double value, double min, double max) {
        if (min > max) {
            return value;
        }

        return Math.max(min, Math.min(max, value));
    }

    /**
     * Parse a human-readable duration to seconds.
     * Supports plain seconds (for example "120") and tokenized values
     * (for example "2w7d1h5m30s").
     *
     * @param value the value to parse
     *
     * @return parsed seconds, or 0 if invalid
     */
    public static long parseSeconds(@Nullable String value) {
        return parseSeconds(value, 0L);
    }

    /**
     * Parse a human-readable duration to seconds.
     * Supports plain seconds (for example "120") and tokenized values
     * (for example "2w7d1h5m30s").
     *
     * @param value the value to parse
     * @param fallbackSeconds fallback value when parsing fails
     *
     * @return parsed seconds, or fallback when invalid
     */
    public static long parseSeconds(@Nullable String value, long fallbackSeconds) {
        if (value == null) {
            return fallbackSeconds;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return fallbackSeconds;
        }

        if (normalized.matches("\\d+")) {
            try {
                return Long.parseLong(normalized);
            } catch (NumberFormatException ignored) {
                return fallbackSeconds;
            }
        }

        Matcher matcher = TIME_TOKEN_PATTERN.matcher(normalized);
        int consumed = 0;
        long totalSeconds = 0L;
        while (matcher.find()) {
            if (matcher.start() != consumed) {
                return fallbackSeconds;
            }

            long number;
            try {
                number = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return fallbackSeconds;
            }

            long multiplier;
            switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
                case 'w':
                    multiplier = 604800L;
                    break;
                case 'd':
                    multiplier = 86400L;
                    break;
                case 'h':
                    multiplier = 3600L;
                    break;
                case 'm':
                    multiplier = 60L;
                    break;
                case 's':
                    multiplier = 1L;
                    break;
                default:
                    return fallbackSeconds;
            }

            long chunk;
            try {
                chunk = Math.multiplyExact(number, multiplier);
                totalSeconds = Math.addExact(totalSeconds, chunk);
            } catch (ArithmeticException ignored) {
                return Long.MAX_VALUE;
            }

            consumed = matcher.end();
        }

        return consumed == normalized.length() ? totalSeconds : fallbackSeconds;
    }

    /**
     * Format a duration into a readable string.
     *
     * @param duration duration value
     * @param sourceUnit source unit for duration
     * @param condensed true for short style (for example "1h 5m"), false for full style
     * @param omissions units to omit from output
     *
     * @return formatted duration
     */
    @NotNull
    public static String getFormattedTime(long duration, @NotNull TimeUnit sourceUnit, boolean condensed, TimeUnit @NotNull... omissions) {
        if (sourceUnit == null) {
            sourceUnit = TimeUnit.SECONDS;
        }

        Set<TimeUnit> omittedUnits = EnumSet.noneOf(TimeUnit.class);
        if (omissions != null) {
            for (TimeUnit omission : omissions) {
                if (omission != null) {
                    omittedUnits.add(omission);
                }
            }
        }

        long totalSeconds = TimeUnit.SECONDS.convert(Math.max(duration, 0L), sourceUnit);
        if (totalSeconds <= 0L) {
            return condensed ? "0s" : "0 seconds";
        }

        long weeks = totalSeconds / 604800L;
        totalSeconds %= 604800L;

        long days = totalSeconds / 86400L;
        totalSeconds %= 86400L;

        long hours = totalSeconds / 3600L;
        totalSeconds %= 3600L;

        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder builder = new StringBuilder();
        appendUnit(builder, weeks, "w", "week", condensed, false);
        appendUnit(builder, days, "d", "day", condensed, omittedUnits.contains(TimeUnit.DAYS));
        appendUnit(builder, hours, "h", "hour", condensed, omittedUnits.contains(TimeUnit.HOURS));
        appendUnit(builder, minutes, "m", "minute", condensed, omittedUnits.contains(TimeUnit.MINUTES));
        appendUnit(builder, seconds, "s", "second", condensed, omittedUnits.contains(TimeUnit.SECONDS));

        if (builder.length() == 0) {
            return condensed ? "0s" : "0 seconds";
        }

        return builder.toString();
    }

    private static void appendUnit(@NotNull StringBuilder builder, long value, @NotNull String condensedSuffix, @NotNull String verboseUnit, boolean condensed, boolean omitted) {
        if (omitted || value <= 0L) {
            return;
        }

        if (builder.length() > 0) {
            builder.append(condensed ? " " : ", ");
        }

        if (condensed) {
            builder.append(value).append(condensedSuffix);
            return;
        }

        builder.append(value).append(" ").append(verboseUnit);
        if (value != 1L) {
            builder.append("s");
        }
    }

}
