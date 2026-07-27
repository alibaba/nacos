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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Strict, case-sensitive Agent version value defined by the Agent and RAD specifications.
 *
 * @author Nacos
 */
final class AgentVersion implements Comparable<AgentVersion>, Serializable {
    
    private static final long serialVersionUID = -3353506295787518826L;
    
    private static final int MAX_LENGTH = 64;
    
    private static final Pattern CORE_NUMBER_PATTERN = Pattern.compile("0|[1-9][0-9]*");
    
    private static final Pattern PRERELEASE_IDENTIFIER_PATTERN = Pattern.compile("[0-9A-Za-z-]+");
    
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]+");
    
    private final String value;
    
    private final BigInteger major;
    
    private final BigInteger minor;
    
    private final BigInteger patch;
    
    private final List<PrereleaseIdentifier> prerelease;
    
    private AgentVersion(String value, BigInteger major, BigInteger minor, BigInteger patch,
        List<PrereleaseIdentifier> prerelease) {
        this.value = value;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = prerelease;
    }
    
    /**
     * Parse a strict Agent version. The input is never trimmed or case-folded.
     *
     * @param value version text
     * @return parsed version
     * @throws IllegalArgumentException when the value is not a valid Agent version
     */
    static AgentVersion parse(String value) {
        if (value == null || value.length() > MAX_LENGTH) {
            throw invalidVersion(value);
        }
        int prereleaseSeparator = value.indexOf('-');
        String core = prereleaseSeparator < 0 ? value : value.substring(0, prereleaseSeparator);
        String prereleaseText =
            prereleaseSeparator < 0 ? null : value.substring(prereleaseSeparator + 1);
        String[] coreNumbers = core.split("\\.", -1);
        if (coreNumbers.length != 3) {
            throw invalidVersion(value);
        }
        for (String number : coreNumbers) {
            if (!CORE_NUMBER_PATTERN.matcher(number).matches()) {
                throw invalidVersion(value);
            }
        }
        
        List<PrereleaseIdentifier> identifiers = Collections.emptyList();
        if (prereleaseText != null) {
            if (prereleaseText.isEmpty()) {
                throw invalidVersion(value);
            }
            String[] parts = prereleaseText.split("\\.", -1);
            identifiers = new ArrayList<PrereleaseIdentifier>(parts.length);
            for (String part : parts) {
                if (!PRERELEASE_IDENTIFIER_PATTERN.matcher(part).matches()) {
                    throw invalidVersion(value);
                }
                boolean numeric = NUMBER_PATTERN.matcher(part).matches();
                if (numeric && part.length() > 1 && part.charAt(0) == '0') {
                    throw invalidVersion(value);
                }
                identifiers.add(new PrereleaseIdentifier(part, numeric));
            }
            identifiers = Collections.unmodifiableList(identifiers);
        }
        
        return new AgentVersion(value, new BigInteger(coreNumbers[0]),
            new BigInteger(coreNumbers[1]),
            new BigInteger(coreNumbers[2]), identifiers);
    }
    
    /**
     * Test whether a string is a valid Agent version.
     *
     * @param value version text
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
     * Return the original version text.
     *
     * @return original version text
     */
    String getValue() {
        return value;
    }
    
    @Override
    public int compareTo(AgentVersion other) {
        Objects.requireNonNull(other, "other");
        int result = major.compareTo(other.major);
        if (result != 0) {
            return result;
        }
        result = minor.compareTo(other.minor);
        if (result != 0) {
            return result;
        }
        result = patch.compareTo(other.patch);
        if (result != 0) {
            return result;
        }
        if (prerelease.isEmpty()) {
            return other.prerelease.isEmpty() ? 0 : 1;
        }
        if (other.prerelease.isEmpty()) {
            return -1;
        }
        int commonLength = Math.min(prerelease.size(), other.prerelease.size());
        for (int i = 0; i < commonLength; i++) {
            result = prerelease.get(i).compareTo(other.prerelease.get(i));
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AgentVersion)) {
            return false;
        }
        AgentVersion other = (AgentVersion) obj;
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
    
    private static IllegalArgumentException invalidVersion(String value) {
        return new IllegalArgumentException("Invalid Agent version: " + value);
    }
    
    private static final class PrereleaseIdentifier
        implements Comparable<PrereleaseIdentifier>, Serializable {
        
        private static final long serialVersionUID = 8442073644426284353L;
        
        private final String value;
        
        private final boolean numeric;
        
        private final BigInteger numericValue;
        
        private PrereleaseIdentifier(String value, boolean numeric) {
            this.value = value;
            this.numeric = numeric;
            this.numericValue = numeric ? new BigInteger(value) : null;
        }
        
        @Override
        public int compareTo(PrereleaseIdentifier other) {
            if (numeric && other.numeric) {
                return numericValue.compareTo(other.numericValue);
            }
            if (numeric != other.numeric) {
                return numeric ? -1 : 1;
            }
            return value.compareTo(other.value);
        }
    }
}
