package net.ixdarklord.folios.api;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The JSON formats books are written in. A book says which in its book.json, {@code "format_version": "3.0"}: the
 * Folios version it was written for. A format is numbered by the Folios release that changed the JSON, and a release
 * that changes nothing keeps the format before it, so any Folios version can be named: the newest format at or below
 * it is used ({@code "3.2"} reads as 3.0 while the formats are 3.0 and 3.5).
 * <p>
 * A book without {@code "format_version"} was written for Folios 2.x: it is read as format {@link #LEGACY}, upgraded to
 * the current format as it loads, with a warning in the log.
 */
public final class FoliosFormat {
    /** The format of books written for Folios 2.x, and of any book that doesn't say. */
    public static final String LEGACY = "2.0";
    /** The format this Folios reads, and writes its upgrades in. */
    public static final String CURRENT = "3.0";
    /** Every format so far, oldest first. */
    public static final List<String> KNOWN = List.of(LEGACY, CURRENT);
    /** The Folios release this is: a book naming a later one needs a newer Folios. */
    public static final String RELEASE = "3.0";

    private FoliosFormat() {}

    /** A version's numbers ({@code "3.10"} is 3 then 10); null when it isn't dot-separated numbers. */
    public static int @Nullable [] parse(String version) {
        String[] parts = version.strip().split("\\.", -1);
        if (parts.length == 0 || parts[0].isEmpty()) return null;
        int[] numbers = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 9 || !part.chars().allMatch(Character::isDigit)) return null;
            numbers[index] = Integer.parseInt(part);
        }
        return numbers;
    }

    /** Compares two versions number by number, a missing number counting as 0 ({@code "3"} is {@code "3.0"}). */
    public static int compare(String first, String second) {
        int[] a = parse(first);
        int[] b = parse(second);
        if (a == null || b == null) throw new IllegalArgumentException("Not a version: " + (a == null ? first : second));
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            int x = index < a.length ? a[index] : 0;
            int y = index < b.length ? b[index] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    /** The format a book naming {@code version} is read in: the newest known at or below it, {@link #LEGACY} below them all. */
    public static String of(String version) {
        String found = LEGACY;
        for (String known : KNOWN) {
            if (compare(known, version) <= 0) found = known;
        }
        return found;
    }

    /** Whether a book naming {@code version} was written for a Folios newer than this one. */
    public static boolean isNewer(String version) {
        return compare(version, RELEASE) > 0;
    }
}
