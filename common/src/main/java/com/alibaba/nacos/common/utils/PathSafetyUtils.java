/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utilities for resolving untrusted names without changing the intended filesystem target.
 *
 * <p>This class rejects directory-control syntax, path separators, absolute paths and normalized
 * paths that do not preserve the intended hierarchy. Filesystem-specific aliases are outside this
 * utility's current scope.</p>
 *
 * <p>The checks are lexical and do not protect a service-owned directory from local symbolic-link
 * replacement.</p>
 *
 * @author Nacos
 */
public final class PathSafetyUtils {
    
    private static final String CURRENT_DIRECTORY = ".";
    
    private static final String PARENT_DIRECTORY = "..";
    
    private PathSafetyUtils() {
    }
    
    /**
     * Validate one untrusted direct-child file name.
     *
     * @param childName untrusted direct-child name
     * @throws IllegalArgumentException when the name may select another filesystem target
     */
    public static void validateDirectChildName(String childName) {
        validatePathSegment(childName);
        try {
            Path childPath = Paths.get(childName);
            if (childPath.isAbsolute() || childPath.getRoot() != null
                || childPath.getNameCount() != 1) {
                throw unsafePathException();
            }
        } catch (InvalidPathException e) {
            throw unsafePathException(e);
        }
    }
    
    /**
     * Resolve one untrusted direct-child file name below a base directory.
     *
     * @param basePath base directory
     * @param childName untrusted direct-child name
     * @return normalized direct-child path
     * @throws IllegalArgumentException when the name may select another filesystem target
     */
    public static Path resolveDirectChild(Path basePath, String childName) {
        validatePathSegment(childName);
        Path normalizedBase = normalizeBasePath(basePath);
        try {
            Path childPath = normalizedBase.getFileSystem().getPath(childName);
            Path targetPath = basePath.resolve(childPath).normalize();
            Path normalizedTarget = targetPath.toAbsolutePath().normalize();
            if (childPath.isAbsolute() || childPath.getRoot() != null
                || childPath.getNameCount() != 1
                || !normalizedBase.equals(normalizedTarget.getParent())) {
                throw unsafePathException();
            }
            return targetPath;
        } catch (InvalidPathException e) {
            throw unsafePathException(e);
        }
    }
    
    /**
     * Normalize and validate an archive entry name using {@code /} as its separator.
     *
     * <p>Backslashes are treated as separators so that archives produced on Windows resolve to
     * the same hierarchy on Unix-like systems.</p>
     *
     * @param entryName untrusted archive entry name
     * @return validated archive entry name with {@code /} separators
     * @throws IllegalArgumentException when the name may select another filesystem target
     */
    public static String normalizeArchiveEntryName(String entryName) {
        if (entryName == null) {
            throw unsafePathException();
        }
        String normalizedName = entryName.replace('\\', '/');
        if (normalizedName.endsWith("/")) {
            normalizedName = normalizedName.substring(0, normalizedName.length() - 1);
        }
        if (normalizedName.isEmpty() || hasWindowsDrivePrefix(normalizedName)) {
            throw unsafePathException();
        }
        String[] segments = normalizedName.split("/", -1);
        StringBuilder result = new StringBuilder(normalizedName.length());
        for (String segment : segments) {
            validatePathSegment(segment);
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(segment);
        }
        return result.toString();
    }
    
    /**
     * Resolve an untrusted archive entry below a destination directory.
     *
     * @param basePath archive destination directory
     * @param entryName untrusted archive entry name
     * @return normalized descendant path
     * @throws IllegalArgumentException when the entry may select another filesystem target
     */
    public static Path resolveArchiveEntry(Path basePath, String entryName) {
        String normalizedName = normalizeArchiveEntryName(entryName);
        Path normalizedBase = normalizeBasePath(basePath);
        try {
            Path relativePath = normalizedBase.getFileSystem().getPath(normalizedName);
            Path targetPath = basePath.resolve(relativePath).normalize();
            Path normalizedTarget = targetPath.toAbsolutePath().normalize();
            if (relativePath.isAbsolute() || relativePath.getRoot() != null
                || normalizedTarget.equals(normalizedBase)
                || !normalizedTarget.startsWith(normalizedBase)) {
                throw unsafePathException();
            }
            return targetPath;
        } catch (InvalidPathException e) {
            throw unsafePathException(e);
        }
    }
    
    private static Path normalizeBasePath(Path basePath) {
        if (basePath == null) {
            throw unsafePathException();
        }
        return basePath.toAbsolutePath().normalize();
    }
    
    private static void validatePathSegment(String segment) {
        if (segment == null || segment.trim().isEmpty()
            || CURRENT_DIRECTORY.equals(segment) || PARENT_DIRECTORY.equals(segment)
            || segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0
            || segment.indexOf('\0') >= 0) {
            throw unsafePathException();
        }
    }
    
    private static boolean hasWindowsDrivePrefix(String path) {
        return path.length() > 1 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':';
    }
    
    private static IllegalArgumentException unsafePathException() {
        return new IllegalArgumentException("Path name may resolve to an unintended target");
    }
    
    private static IllegalArgumentException unsafePathException(Exception cause) {
        return new IllegalArgumentException("Path name may resolve to an unintended target",
            cause);
    }
}
