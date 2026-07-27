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

package com.alibaba.nacos.api.ai.utils;

import java.io.Serializable;
import java.util.Objects;

/**
 * One continuous Maven-style interval whose boundaries use {@link AgentVersion} precedence.
 *
 * @author Nacos
 */
final class AgentVersionRange implements Serializable {
    
    private static final long serialVersionUID = 6739273097318730490L;
    
    private static final int MAX_LENGTH = 256;
    
    private final AgentVersion lowerBound;
    
    private final AgentVersion upperBound;
    
    private final boolean lowerInclusive;
    
    private final boolean upperInclusive;
    
    private final String value;
    
    private AgentVersionRange(AgentVersion lowerBound, AgentVersion upperBound,
        boolean lowerInclusive,
        boolean upperInclusive, String value) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.lowerInclusive = lowerInclusive;
        this.upperInclusive = upperInclusive;
        this.value = value;
    }
    
    /**
     * Parse and canonicalize one exact version or one continuous interval.
     *
     * @param value range text
     * @return canonical range
     * @throws IllegalArgumentException when the range is invalid
     */
    static AgentVersionRange parse(String value) {
        if (value == null || value.length() < 3 || value.length() > MAX_LENGTH) {
            throw invalidRange(value);
        }
        if (value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']'
            && value.indexOf(',') < 0) {
            AgentVersion exact = AgentVersion.parse(value.substring(1, value.length() - 1));
            return new AgentVersionRange(exact, exact, true, true, '[' + exact.toString() + ']');
        }
        
        char lowerDelimiter = value.charAt(0);
        char upperDelimiter = value.charAt(value.length() - 1);
        if ((lowerDelimiter != '[' && lowerDelimiter != '(')
            || (upperDelimiter != ']' && upperDelimiter != ')')) {
            throw invalidRange(value);
        }
        int comma = value.indexOf(',');
        if (comma < 0 || comma != value.lastIndexOf(',')) {
            throw invalidRange(value);
        }
        String lowerText = value.substring(1, comma);
        String upperText = value.substring(comma + 1, value.length() - 1);
        if (lowerText.isEmpty() && upperText.isEmpty()) {
            throw invalidRange(value);
        }
        if (lowerText.isEmpty() && lowerDelimiter != '(') {
            throw invalidRange(value);
        }
        if (upperText.isEmpty() && upperDelimiter != ')') {
            throw invalidRange(value);
        }
        
        AgentVersion lower = lowerText.isEmpty() ? null : AgentVersion.parse(lowerText);
        AgentVersion upper = upperText.isEmpty() ? null : AgentVersion.parse(upperText);
        boolean includeLower = lowerDelimiter == '[';
        boolean includeUpper = upperDelimiter == ']';
        if (lower != null && upper != null) {
            int comparison = lower.compareTo(upper);
            if (comparison > 0 || comparison == 0 && (!includeLower || !includeUpper)) {
                throw invalidRange(value);
            }
            if (comparison == 0) {
                String canonical = '[' + lower.toString() + ']';
                return new AgentVersionRange(lower, upper, true, true, canonical);
            }
        }
        
        String canonical =
            new StringBuilder().append(lowerDelimiter).append(lower == null ? "" : lower)
                .append(',').append(upper == null ? "" : upper).append(upperDelimiter).toString();
        return new AgentVersionRange(lower, upper, includeLower, includeUpper, canonical);
    }
    
    /**
     * Create an exact range for one version.
     *
     * @param version exact version
     * @return exact range
     */
    static AgentVersionRange exact(AgentVersion version) {
        Objects.requireNonNull(version, "version");
        return parse('[' + version.toString() + ']');
    }
    
    /**
     * Test whether a string is a valid Agent version range.
     *
     * @param value range text
     * @return {@code true} when valid
     */
    static boolean isValid(String value) {
        try {
            parse(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
    
    /**
     * Determine whether the supplied version is within this range.
     *
     * @param version version to test
     * @return {@code true} when contained
     */
    boolean contains(AgentVersion version) {
        Objects.requireNonNull(version, "version");
        if (lowerBound != null) {
            int comparison = version.compareTo(lowerBound);
            if (comparison < 0 || comparison == 0 && !lowerInclusive) {
                return false;
            }
        }
        if (upperBound != null) {
            int comparison = version.compareTo(upperBound);
            if (comparison > 0 || comparison == 0 && !upperInclusive) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Parse and determine whether the supplied version is within this range.
     *
     * @param version version text to test
     * @return {@code true} when contained
     * @throws IllegalArgumentException when the version is invalid
     */
    boolean contains(String version) {
        return contains(AgentVersion.parse(version));
    }
    
    /**
     * Return the lower boundary, or {@code null} for an unbounded lower side.
     *
     * @return lower boundary
     */
    AgentVersion getLowerBound() {
        return lowerBound;
    }
    
    /**
     * Return the upper boundary, or {@code null} for an unbounded upper side.
     *
     * @return upper boundary
     */
    AgentVersion getUpperBound() {
        return upperBound;
    }
    
    /**
     * Return whether the lower boundary is inclusive.
     *
     * @return lower-bound inclusion
     */
    boolean isLowerInclusive() {
        return lowerInclusive;
    }
    
    /**
     * Return whether the upper boundary is inclusive.
     *
     * @return upper-bound inclusion
     */
    boolean isUpperInclusive() {
        return upperInclusive;
    }
    
    /**
     * Return the canonical range text.
     *
     * @return canonical range text
     */
    String getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AgentVersionRange)) {
            return false;
        }
        AgentVersionRange other = (AgentVersionRange) obj;
        return value.equals(other.value);
    }
    
    @Override
    public int hashCode() {
        return value.hashCode();
    }
    
    @Override
    public String toString() {
        return value;
    }
    
    private static IllegalArgumentException invalidRange(String value) {
        return new IllegalArgumentException("Invalid Agent version range: " + value);
    }
}
