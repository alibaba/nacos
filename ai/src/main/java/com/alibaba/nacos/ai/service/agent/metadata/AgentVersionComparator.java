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

package com.alibaba.nacos.ai.service.agent.metadata;

import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server-side comparator for the strict, case-sensitive Agent Version contract.
 *
 * <p>The public API validator owns the accepted grammar. This comparator validates through that
 * contract first and implements only precedence ordering for persistence projections.</p>
 *
 * @author Nacos
 */
public final class AgentVersionComparator {
    
    private AgentVersionComparator() {
    }
    
    /**
     * Compare two Agent Version values by RAD SemVer precedence.
     *
     * @param left left Version
     * @param right right Version
     * @return a negative value, zero, or a positive value as left is lower, equal, or higher
     */
    public static int compare(String left, String right) {
        AgentValidationUtils.validateVersion(left);
        AgentValidationUtils.validateVersion(right);
        return ParsedVersion.parse(left).compareTo(ParsedVersion.parse(right));
    }
    
    private static final class ParsedVersion implements Comparable<ParsedVersion> {
        
        private final BigInteger major;
        
        private final BigInteger minor;
        
        private final BigInteger patch;
        
        private final List<PrereleaseIdentifier> prerelease;
        
        private ParsedVersion(BigInteger major, BigInteger minor, BigInteger patch,
            List<PrereleaseIdentifier> prerelease) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.prerelease = prerelease;
        }
        
        private static ParsedVersion parse(String version) {
            int separator = version.indexOf('-');
            String core = separator < 0 ? version : version.substring(0, separator);
            String[] numbers = core.split("\\.");
            List<PrereleaseIdentifier> prerelease = Collections.emptyList();
            if (separator >= 0) {
                String[] identifiers = version.substring(separator + 1).split("\\.");
                prerelease = new ArrayList<PrereleaseIdentifier>(identifiers.length);
                for (String identifier : identifiers) {
                    prerelease.add(new PrereleaseIdentifier(identifier));
                }
            }
            return new ParsedVersion(new BigInteger(numbers[0]), new BigInteger(numbers[1]),
                new BigInteger(numbers[2]), prerelease);
        }
        
        @Override
        public int compareTo(ParsedVersion other) {
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
    }
    
    private static final class PrereleaseIdentifier
        implements Comparable<PrereleaseIdentifier> {
        
        private final String value;
        
        private final BigInteger numericValue;
        
        private PrereleaseIdentifier(String value) {
            this.value = value;
            this.numericValue = isNumeric(value) ? new BigInteger(value) : null;
        }
        
        @Override
        public int compareTo(PrereleaseIdentifier other) {
            if (numericValue != null && other.numericValue != null) {
                return numericValue.compareTo(other.numericValue);
            }
            if (numericValue != null || other.numericValue != null) {
                return numericValue != null ? -1 : 1;
            }
            return value.compareTo(other.value);
        }
        
        private static boolean isNumeric(String value) {
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                    return false;
                }
            }
            return true;
        }
    }
}
