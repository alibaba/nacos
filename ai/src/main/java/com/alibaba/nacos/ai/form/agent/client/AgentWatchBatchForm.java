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

package com.alibaba.nacos.ai.form.agent.client;

import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchItem;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchRequest;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;

import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Form binding for one request-scoped HTTP Agent Watch batch.
 *
 * @author Nacos
 */
public class AgentWatchBatchForm implements NacosForm {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    static final int MAX_WATCH_ITEMS = 1000;
    
    private static final long MIN_TIMEOUT_MILLIS = 1000L;
    
    private static final long MAX_TIMEOUT_MILLIS = 60000L;
    
    private static final Pattern CLIENT_WATCH_ID_PATTERN =
        Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    
    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile(
        Pattern.quote(AgentDiscoveryCanonicalizer.ALGORITHM_ID) + ":[0-9a-f]{64}");
    
    private Long generation;
    
    private Long timeoutMillis;
    
    private String watches;
    
    @Override
    public void validate() throws NacosApiException {
        toRequest();
    }
    
    /**
     * Parse, canonicalize, and validate the complete current Watch set.
     *
     * @return validated request
     * @throws NacosApiException when any field violates the Watch binding
     */
    public AgentWatchBatchRequest toRequest() throws NacosApiException {
        if (generation == null || generation < 0L) {
            throw invalid("Request parameter `generation` must be greater than or equal to 0.");
        }
        if (timeoutMillis == null || timeoutMillis < MIN_TIMEOUT_MILLIS
            || timeoutMillis > MAX_TIMEOUT_MILLIS) {
            throw invalid("Request parameter `timeoutMillis` must be between 1000 and 60000.");
        }
        AgentWatchBatchRequest result = new AgentWatchBatchRequest();
        result.setGeneration(generation);
        result.setTimeoutMillis(timeoutMillis);
        result.setWatches(parseAndValidateWatches(watches));
        return result;
    }
    
    /**
     * Extract the single effective namespace from a Watch JSON form field for authorization.
     *
     * @param watchesJson complete Watch list JSON
     * @return effective namespace
     * @throws NacosApiException when the list is malformed, empty, or mixes namespaces
     */
    public static String extractNamespaceId(String watchesJson) throws NacosApiException {
        return parseAndValidateWatches(watchesJson).get(0).getDiscoveryRequest().getNamespaceId();
    }
    
    /**
     * Return the UTF-8 bytes occupied by the JSON-valued form field.
     *
     * @return payload bytes, or zero before a value is supplied
     */
    public int getWatchPayloadBytes() {
        return watches == null ? 0 : watches.getBytes(StandardCharsets.UTF_8).length;
    }
    
    private static List<AgentWatchBatchItem> parseAndValidateWatches(String watchesJson)
        throws NacosApiException {
        List<AgentWatchBatchItem> source = AgentClientFormJsonParser.parseOptional("watches",
            watchesJson, new NacosTypeReference<List<AgentWatchBatchItem>>() {
            });
        if (source == null || source.isEmpty()) {
            throw invalid("Request parameter `watches` must contain at least one item.");
        }
        if (source.size() > MAX_WATCH_ITEMS) {
            throw invalid("Request parameter `watches` must contain at most 1000 items.");
        }
        List<AgentWatchBatchItem> result = new ArrayList<AgentWatchBatchItem>(source.size());
        Set<String> ids = new HashSet<String>(source.size());
        String namespaceId = null;
        for (AgentWatchBatchItem each : source) {
            if (each == null) {
                throw invalid("Request parameter `watches` must not contain null items.");
            }
            String clientWatchId = each.getClientWatchId();
            if (clientWatchId == null
                || !CLIENT_WATCH_ID_PATTERN.matcher(clientWatchId).matches()) {
                throw invalid("Watch `clientWatchId` must match `[A-Za-z0-9._:-]+` and contain "
                    + "1 to 128 characters.");
            }
            if (!ids.add(clientWatchId)) {
                throw invalid("Request parameter `watches` contains duplicate clientWatchId `"
                    + clientWatchId + "`.");
            }
            if (each.getDiscoveryRequest() == null) {
                throw invalid("Watch `discoveryRequest` must not be null.");
            }
            AgentDiscoveryRequest discoveryRequest;
            try {
                discoveryRequest = AgentDiscoveryCanonicalizer.canonicalizeRequest(
                    each.getDiscoveryRequest());
            } catch (IllegalArgumentException e) {
                throw invalid("Watch `discoveryRequest` is invalid: " + e.getMessage());
            }
            if (namespaceId == null) {
                namespaceId = discoveryRequest.getNamespaceId();
            } else if (!namespaceId.equals(discoveryRequest.getNamespaceId())) {
                throw invalid("Request parameter `watches` must use one effective namespace.");
            }
            String fingerprint = each.getMaterializedFingerprint();
            if (fingerprint == null || !FINGERPRINT_PATTERN.matcher(fingerprint).matches()) {
                throw invalid("Watch `materializedFingerprint` is invalid.");
            }
            AgentWatchBatchItem item = new AgentWatchBatchItem();
            item.setClientWatchId(clientWatchId);
            item.setDiscoveryRequest(discoveryRequest);
            item.setMaterializedFingerprint(fingerprint);
            result.add(item);
        }
        return result;
    }
    
    private static NacosApiException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    public Long getGeneration() {
        return generation;
    }
    
    public void setGeneration(Long generation) {
        this.generation = generation;
    }
    
    public Long getTimeoutMillis() {
        return timeoutMillis;
    }
    
    public void setTimeoutMillis(Long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
    
    public String getWatches() {
        return watches;
    }
    
    public void setWatches(String watches) {
        this.watches = watches;
    }
}
