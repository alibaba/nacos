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

import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalogEntry;
import com.alibaba.nacos.api.ai.utils.AgentModelValidator;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Rebuilds the server-derived Agent Version catalog, latest label, and protocol set.
 *
 * @author Nacos
 */
public final class AgentVersionCatalogBuilder {
    
    private static final int MAX_PROTOCOLS_PER_VERSION = 16;
    
    private AgentVersionCatalogBuilder() {
    }
    
    /**
     * Build deterministic derived metadata from online Version facts.
     *
     * <p>The map key is an exact online Version and the value is its CallInterface protocol order.
     * Custom labels that point to non-online Versions remain in the normalized label map but do not
     * enter the online catalog. An absent or stale {@code latest} label is repaired to the greatest
     * online Version.</p>
     *
     * @param onlineVersionProtocols online Version to ordered protocol mapping
     * @param labels complete label facts
     * @return deterministic build result with immutable label and protocol collections
     */
    public static Result build(Map<String, List<String>> onlineVersionProtocols,
        Map<String, String> labels) {
        if (onlineVersionProtocols == null) {
            throw new IllegalArgumentException("onlineVersionProtocols must not be null");
        }
        if (labels == null) {
            throw new IllegalArgumentException("labels must not be null");
        }
        
        Map<String, List<String>> protocolsByVersion =
            validateAndCopyProtocols(onlineVersionProtocols);
        Map<String, String> normalizedLabels = validateAndSortLabels(labels);
        List<String> versions = new ArrayList<String>(protocolsByVersion.keySet());
        versions.sort(new Comparator<String>() {
            
            @Override
            public int compare(String left, String right) {
                return AgentVersionComparator.compare(right, left);
            }
        });
        
        if (versions.isEmpty()) {
            normalizedLabels.remove("latest");
        } else {
            String latest = normalizedLabels.get("latest");
            if (latest == null || !protocolsByVersion.containsKey(latest)) {
                normalizedLabels.put("latest", versions.get(0));
                normalizedLabels = sortLabels(normalizedLabels);
            }
        }
        
        AgentVersionCatalog catalog = new AgentVersionCatalog();
        catalog.setLatestVersion(normalizedLabels.get("latest"));
        List<AgentVersionCatalogEntry> entries =
            new ArrayList<AgentVersionCatalogEntry>(versions.size());
        Set<String> protocols = new TreeSet<String>();
        for (String version : versions) {
            AgentVersionCatalogEntry entry = new AgentVersionCatalogEntry();
            entry.setVersion(version);
            entry.setLabels(labelsForVersion(normalizedLabels, version));
            List<String> versionProtocols = protocolsByVersion.get(version);
            entry.setProtocols(Collections.unmodifiableList(versionProtocols));
            protocols.addAll(versionProtocols);
            entries.add(entry);
        }
        catalog.setOnlineVersions(Collections.unmodifiableList(entries));
        AgentModelValidator.validateVersionCatalog(catalog);
        return new Result(catalog, normalizedLabels, new ArrayList<String>(protocols));
    }
    
    private static Map<String, List<String>> validateAndCopyProtocols(
        Map<String, List<String>> onlineVersionProtocols) {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> entry : onlineVersionProtocols.entrySet()) {
            String version = entry.getKey();
            AgentValidationUtils.validateVersion(version);
            List<String> protocols = entry.getValue();
            if (protocols == null || protocols.isEmpty()
                || protocols.size() > MAX_PROTOCOLS_PER_VERSION) {
                throw new IllegalArgumentException(
                    "Online Agent Version protocols must contain 1 to "
                        + MAX_PROTOCOLS_PER_VERSION + " values");
            }
            Set<String> uniqueProtocols = new HashSet<String>();
            List<String> protocolCopy = new ArrayList<String>(protocols.size());
            for (String protocol : protocols) {
                AgentValidationUtils.validateProtocol(protocol);
                if (!uniqueProtocols.add(protocol)) {
                    throw new IllegalArgumentException(
                        "Duplicate protocol for Agent Version " + version + ": " + protocol);
                }
                protocolCopy.add(protocol);
            }
            result.put(version, protocolCopy);
        }
        return result;
    }
    
    private static Map<String, String> validateAndSortLabels(Map<String, String> labels) {
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            AgentValidationUtils.validateLabel(entry.getKey());
            AgentValidationUtils.validateVersion(entry.getValue());
        }
        return sortLabels(labels);
    }
    
    private static Map<String, String> sortLabels(Map<String, String> labels) {
        return new LinkedHashMap<String, String>(new TreeMap<String, String>(labels));
    }
    
    private static List<String> labelsForVersion(Map<String, String> labels, String version) {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, String> label : labels.entrySet()) {
            if (!"latest".equals(label.getKey()) && version.equals(label.getValue())) {
                result.add(label.getKey());
            }
        }
        return Collections.unmodifiableList(result);
    }
    
    /**
     * Result of rebuilding all resource-level facts derived from online Versions.
     */
    public static final class Result {
        
        private final AgentVersionCatalog versionCatalog;
        
        private final Map<String, String> labels;
        
        private final List<String> protocols;
        
        private Result(AgentVersionCatalog versionCatalog, Map<String, String> labels,
            List<String> protocols) {
            this.versionCatalog = versionCatalog;
            this.labels = Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(labels));
            this.protocols = Collections.unmodifiableList(protocols);
        }
        
        public AgentVersionCatalog getVersionCatalog() {
            return versionCatalog;
        }
        
        public Map<String, String> getLabels() {
            return labels;
        }
        
        public List<String> getProtocols() {
            return protocols;
        }
    }
}
