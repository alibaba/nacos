/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version utils.
 *
 * @author xingxuechao on:2019/2/27 12:32 PM
 */
public class VersionUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionUtils.class);
    
    private VersionUtils() {
    }
    
    public static String version;
    
    private static String clientVersion;
    
    /**
     * current version.
     */
    public static final String VERSION_PLACEHOLDER = "${project.version}";
    
    private static final String NACOS_VERSION_FILE = "nacos-version.txt";
    
    static {
        try (InputStream in =
            VersionUtils.class.getClassLoader().getResourceAsStream(NACOS_VERSION_FILE)) {
            Properties props = new Properties();
            props.load(in);
            String val = props.getProperty("version");
            if (val != null && !VERSION_PLACEHOLDER.equals(val)) {
                version = val;
                clientVersion = "Nacos-Java-Client:v" + VersionUtils.version;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load nacos version from {}; version fields will remain unset",
                NACOS_VERSION_FILE, e);
        }
    }
    
    /**
     * compare two version who is latest.
     *
     * @param versionA version A, like x.y.z(-beta)
     * @param versionB version B, like x.y.z(-beta)
     * @return compare result
     */
    public static int compareVersion(final String versionA, final String versionB) {
        final String[] sA = versionA.split("\\.");
        final String[] sB = versionB.split("\\.");
        int expectSize = 3;
        if (sA.length != expectSize || sB.length != expectSize) {
            throw new IllegalArgumentException("version must be like x.y.z(-beta)");
        }
        int major =
            Integer.compare(parseVersionPart(sA[0], versionA), parseVersionPart(sB[0], versionB));
        if (major != 0) {
            return major;
        }
        int minor =
            Integer.compare(parseVersionPart(sA[1], versionA), parseVersionPart(sB[1], versionB));
        if (minor != 0) {
            return minor;
        }
        int patchA = parseVersionPart(sA[2].split("-")[0], versionA);
        int patchB = parseVersionPart(sB[2].split("-")[0], versionB);
        return Integer.compare(patchA, patchB);
    }
    
    private static int parseVersionPart(String part, String originalVersion) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "version must be like x.y.z(-beta), got: " + originalVersion, e);
        }
    }
    
    public static String getFullClientVersion() {
        return clientVersion;
    }
    
    // ---- AI Resource Version Utilities (semver x.y.z + legacy vN) ----
    
    private static final Pattern PURE_SEMVER_PATTERN =
        Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z0-9]+(?:\\.[a-zA-Z0-9]+)*))?$");
    
    private static final String DEFAULT_INITIAL_SEMVER = "0.0.1";
    
    /**
     * Normalize a version string to semver format (x.y.z or x.y.z-prerelease). Strips leading 'v'/'V' prefix and
     * validates format.
     *
     * @param version version string, e.g. "1.2.3", "v1.2.3", "0.0.1-beta"
     * @return normalized semver string (e.g. "1.2.3", "0.0.1-beta"), or null if not a valid semver
     */
    public static String normalizeSemver(String version) {
        if (version == null || version.trim().isEmpty()) {
            return null;
        }
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        Matcher matcher = PURE_SEMVER_PATTERN.matcher(v);
        if (!matcher.matches()) {
            return null;
        }
        return v;
    }
    
    /**
     * Check if the given string is a valid semver format (x.y.z, optionally prefixed with v/V).
     *
     * @param version version string
     * @return true if valid semver
     */
    public static boolean isSemver(String version) {
        return normalizeSemver(version) != null;
    }
    
    /**
     * Parse semver string into [major, minor, patch] integer array. Pre-release suffix (e.g. "-beta") is stripped
     * before parsing.
     *
     * @param version version string, e.g. "1.2.3", "v1.2.3", "0.0.1-beta"
     * @return int array of [major, minor, patch], or null if not a valid semver
     */
    public static int[] parseSemverParts(String version) {
        String normalized = normalizeSemver(version);
        if (normalized == null) {
            return null;
        }
        String numericPart = normalized.split("-")[0];
        String[] parts = numericPart.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    
    /**
     * Compare two semver version strings numerically. Pre-release versions have lower precedence than the same version
     * without pre-release (e.g. "1.0.0-beta" &lt; "1.0.0"). When both have pre-release labels, they are compared
     * lexicographically.
     *
     * @param a first version (e.g. "1.2.3", "v1.2.3", "0.0.1-beta")
     * @param b second version
     * @return negative if a &lt; b, 0 if equal, positive if a &gt; b; null values are treated as smallest
     */
    public static int compareSemverVersion(String a, String b) {
        String na = normalizeSemver(a);
        String nb = normalizeSemver(b);
        int[] pa = parseSemverParts(a);
        int[] pb = parseSemverParts(b);
        if (pa == null && pb == null) {
            return 0;
        }
        if (pa == null) {
            return -1;
        }
        if (pb == null) {
            return 1;
        }
        if (pa[0] != pb[0]) {
            return Integer.compare(pa[0], pb[0]);
        }
        if (pa[1] != pb[1]) {
            return Integer.compare(pa[1], pb[1]);
        }
        if (pa[2] != pb[2]) {
            return Integer.compare(pa[2], pb[2]);
        }
        String preA = extractPreRelease(na);
        String preB = extractPreRelease(nb);
        if (preA == null && preB == null) {
            return 0;
        }
        if (preA != null && preB == null) {
            return -1;
        }
        if (preA == null) {
            return 1;
        }
        return preA.compareTo(preB);
    }
    
    /**
     * Extract pre-release label from a normalized semver string.
     *
     * @param normalizedVersion normalized version, e.g. "1.0.0-beta"
     * @return pre-release part (e.g. "beta"), or null if none
     */
    private static String extractPreRelease(String normalizedVersion) {
        if (normalizedVersion == null) {
            return null;
        }
        int idx = normalizedVersion.indexOf('-');
        return idx >= 0 ? normalizedVersion.substring(idx + 1) : null;
    }
    
    /**
     * Increment the patch number of a semver version string. Pre-release suffix is stripped in the result.
     *
     * @param version semver string, e.g. "1.2.3" or "1.2.3-beta"
     * @return incremented version, e.g. "1.2.4"; returns "0.0.1" if input is not valid semver
     */
    public static String nextSemverPatch(String version) {
        int[] parts = parseSemverParts(version);
        if (parts == null) {
            return DEFAULT_INITIAL_SEMVER;
        }
        return parts[0] + "." + parts[1] + "." + (parts[2] + 1);
    }
    
    /**
     * Find the maximum semver version from a list of version strings. Non-semver strings are ignored.
     *
     * @param versions list of version strings (supports x.y.z and x.y.z-prerelease)
     * @return the highest semver string (normalized, without 'v' prefix), or null if none found
     */
    public static String maxSemver(List<String> versions) {
        if (versions == null || versions.isEmpty()) {
            return null;
        }
        String max = null;
        for (String raw : versions) {
            String normalized = normalizeSemver(raw);
            if (normalized == null) {
                continue;
            }
            if (max == null || compareSemverVersion(normalized, max) > 0) {
                max = normalized;
            }
        }
        return max;
    }
    
    /**
     * Parse a legacy version number from "vN" format (e.g. "v3" returns 3).
     *
     * @param version version string, e.g. "v3", "V10"
     * @return the numeric part, or null if not a valid vN format
     */
    public static Integer parseVNumber(String version) {
        if (version == null || version.trim().isEmpty()) {
            return null;
        }
        String normalized = version.trim();
        if (!(normalized.startsWith("v") || normalized.startsWith("V"))
            || normalized.length() <= 1) {
            return null;
        }
        try {
            int numeric = Integer.parseInt(normalized.substring(1));
            return numeric > 0 ? numeric : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    
    /**
     * Check if the given string is a valid vN format (e.g. "v1", "V10").
     *
     * @param version version string
     * @return true if valid vN format
     */
    public static boolean isVNumber(String version) {
        return parseVNumber(version) != null;
    }
    
    /**
     * Find the maximum legacy version number from a list of version strings in "vN" format. Non-vN strings are
     * ignored.
     *
     * @param versions list of version strings
     * @return the highest numeric value, or 0 if none found
     */
    public static int maxVNumber(List<String> versions) {
        int max = 0;
        if (versions == null) {
            return max;
        }
        for (String v : versions) {
            Integer n = parseVNumber(v);
            if (n != null && n > max) {
                max = n;
            }
        }
        return max;
    }
    
    /**
     * Find the version string with the highest numeric suffix from "vN" format versions. Non-vN strings are ignored.
     *
     * @param versions list of version strings
     * @return the version string with the highest number (e.g. "v3"), or null if none found
     */
    public static String maxVNumberVersion(List<String> versions) {
        int max = maxVNumber(versions);
        return max == 0 ? null : "v" + max;
    }
    
    /**
     * Generate the next vN version string by incrementing the highest existing vN number.
     *
     * @param versions list of existing version strings
     * @return next version string, e.g. "v4" if max is "v3"; returns "v1" if no vN versions exist
     */
    public static String nextVNumberVersion(List<String> versions) {
        return "v" + (maxVNumber(versions) + 1);
    }
    
    /**
     * Check if the given version string is a supported format (either semver x.y.z or legacy vN).
     *
     * @param version version string
     * @return true if the version is semver or vN format
     */
    public static boolean isSupportedVersionFormat(String version) {
        return isSemver(version) || isVNumber(version);
    }
    
    /**
     * Check if the target version is strictly greater than the base version. Both versions must be in the same format
     * (semver x.y.z or legacy vN) for comparison.
     *
     * @param target target version string
     * @param base   base version string
     * @return {@code true} if target &gt; base, {@code false} if target &lt;= base or formats are incompatible / not
     *         recognized
     */
    public static boolean isGreaterVersion(String target, String base) {
        String targetSemver = normalizeSemver(target);
        String baseSemver = normalizeSemver(base);
        if (targetSemver != null && baseSemver != null) {
            return compareSemverVersion(targetSemver, baseSemver) > 0;
        }
        Integer targetLegacy = parseVNumber(target);
        Integer baseLegacy = parseVNumber(base);
        if (targetLegacy != null && baseLegacy != null) {
            return targetLegacy > baseLegacy;
        }
        return false;
    }
}
