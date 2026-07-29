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

package com.alibaba.nacos.ai.service.agent.runtime;

import com.alibaba.nacos.ai.service.agent.metadata.AgentVersionComparator;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;

/**
 * Server-internal operations over the public Agent Version range grammar.
 *
 * @author Nacos
 */
final class RuntimeVersionRangeSupport {
    
    private RuntimeVersionRangeSupport() {
    }
    
    static String exact(String version) {
        AgentValidationUtils.validateVersion(version);
        return '[' + version + ']';
    }
    
    static String canonicalize(String versionRange) {
        AgentValidationUtils.validateVersionRange(versionRange);
        ParsedRange parsed = ParsedRange.parse(versionRange);
        if (parsed.lowerBound != null && parsed.upperBound != null
            && parsed.lowerInclusive && parsed.upperInclusive
            && AgentVersionComparator.compare(parsed.lowerBound, parsed.upperBound) == 0) {
            return '[' + parsed.lowerBound + ']';
        }
        return versionRange;
    }
    
    static boolean contains(String versionRange, String version) {
        AgentValidationUtils.validateVersionRange(versionRange);
        AgentValidationUtils.validateVersion(version);
        ParsedRange parsed = ParsedRange.parse(versionRange);
        if (parsed.lowerBound != null) {
            int comparison = AgentVersionComparator.compare(version, parsed.lowerBound);
            if (comparison < 0 || comparison == 0 && !parsed.lowerInclusive) {
                return false;
            }
        }
        if (parsed.upperBound != null) {
            int comparison = AgentVersionComparator.compare(version, parsed.upperBound);
            if (comparison > 0 || comparison == 0 && !parsed.upperInclusive) {
                return false;
            }
        }
        return true;
    }
    
    private static final class ParsedRange {
        
        private final String lowerBound;
        
        private final String upperBound;
        
        private final boolean lowerInclusive;
        
        private final boolean upperInclusive;
        
        private ParsedRange(String lowerBound, String upperBound, boolean lowerInclusive,
            boolean upperInclusive) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.lowerInclusive = lowerInclusive;
            this.upperInclusive = upperInclusive;
        }
        
        private static ParsedRange parse(String value) {
            int comma = value.indexOf(',');
            if (comma < 0) {
                String exact = value.substring(1, value.length() - 1);
                return new ParsedRange(exact, exact, true, true);
            }
            String lower = value.substring(1, comma);
            String upper = value.substring(comma + 1, value.length() - 1);
            return new ParsedRange(lower.isEmpty() ? null : lower,
                upper.isEmpty() ? null : upper, value.charAt(0) == '[',
                value.charAt(value.length() - 1) == ']');
        }
    }
}
