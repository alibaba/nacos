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

package com.alibaba.nacos.plugin.ai.pipeline.spi.impl;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed SkillSpector JSON report.
 *
 * @author nacos
 */
final class SkillSpectorScanReport {
    
    private final int riskScore;
    
    private final String riskSeverity;
    
    private final String riskRecommendation;
    
    private final int issueCount;
    
    private final List<Finding> findings;
    
    private SkillSpectorScanReport(int riskScore, String riskSeverity,
        String riskRecommendation, int issueCount, List<Finding> findings) {
        this.riskScore = riskScore;
        this.riskSeverity = riskSeverity;
        this.riskRecommendation = riskRecommendation;
        this.issueCount = issueCount;
        this.findings = findings;
    }
    
    static SkillSpectorScanReport parse(String json) {
        JsonNode root = JacksonUtils.toObj(json);
        JsonNode risk = root.path("risk_assessment");
        int score = root.has("risk_score") ? root.path("risk_score").asInt()
            : risk.path("score").asInt(-1);
        if (score < 0) {
            throw new IllegalArgumentException("Missing SkillSpector risk score");
        }
        String severity = firstText(root.path("risk_severity"), risk.path("severity"));
        String recommendation = firstText(root.path("risk_recommendation"),
            risk.path("recommendation"));
        JsonNode issues = firstArray(root.path("issues"), root.path("filtered_findings"),
            root.path("findings"));
        List<Finding> findings = parseFindings(issues);
        return new SkillSpectorScanReport(score, severity, recommendation, issues.size(),
            findings);
    }
    
    private static String firstText(JsonNode first, JsonNode second) {
        if (first != null && first.isTextual()) {
            return first.asText();
        }
        if (second != null && second.isTextual()) {
            return second.asText();
        }
        return "UNKNOWN";
    }
    
    private static JsonNode firstArray(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return JacksonUtils.createEmptyArrayNode();
    }
    
    private static List<Finding> parseFindings(JsonNode issues) {
        List<Finding> result = new ArrayList<>();
        if (issues == null || !issues.isArray()) {
            return result;
        }
        for (JsonNode issue : issues) {
            result.add(Finding.from(issue));
        }
        return result;
    }
    
    int getRiskScore() {
        return riskScore;
    }
    
    String getRiskSeverity() {
        return riskSeverity;
    }
    
    String getRiskRecommendation() {
        return riskRecommendation;
    }
    
    int getIssueCount() {
        return issueCount;
    }
    
    List<Finding> getFindings() {
        return findings;
    }
    
    static final class Finding {
        
        private final String id;
        
        private final String category;
        
        private final String severity;
        
        private final String file;
        
        private final int startLine;
        
        private final int endLine;
        
        private final String explanation;
        
        private final String remediation;
        
        private final String codeSnippet;
        
        private Finding(String id, String category, String severity, String file,
            int startLine, int endLine, String explanation, String remediation,
            String codeSnippet) {
            this.id = id;
            this.category = category;
            this.severity = severity;
            this.file = file;
            this.startLine = startLine;
            this.endLine = endLine;
            this.explanation = explanation;
            this.remediation = remediation;
            this.codeSnippet = codeSnippet;
        }
        
        private static Finding from(JsonNode issue) {
            JsonNode location = issue.path("location");
            return new Finding(
                text(issue, "id", "rule_id"),
                text(issue, "category"),
                text(issue, "severity"),
                firstNonBlank(text(location, "file"), text(issue, "file")),
                firstPositive(location.path("start_line").asInt(0),
                    issue.path("start_line").asInt(0)),
                firstPositive(location.path("end_line").asInt(0),
                    issue.path("end_line").asInt(0)),
                firstNonBlank(text(issue, "explanation"), text(issue, "message"),
                    text(issue, "finding")),
                text(issue, "remediation"),
                firstNonBlank(text(issue, "code_snippet"), text(issue, "context")));
        }
        
        private static String text(JsonNode node, String... fieldNames) {
            for (String fieldName : fieldNames) {
                JsonNode value = node.path(fieldName);
                if (value.isTextual() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
            return null;
        }
        
        private static String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }
        
        private static int firstPositive(int... values) {
            for (int value : values) {
                if (value > 0) {
                    return value;
                }
            }
            return 0;
        }
        
        String getId() {
            return id;
        }
        
        String getCategory() {
            return category;
        }
        
        String getSeverity() {
            return severity;
        }
        
        String getFile() {
            return file;
        }
        
        int getStartLine() {
            return startLine;
        }
        
        int getEndLine() {
            return endLine;
        }
        
        String getExplanation() {
            return explanation;
        }
        
        String getRemediation() {
            return remediation;
        }
        
        String getCodeSnippet() {
            return codeSnippet;
        }
    }
}
