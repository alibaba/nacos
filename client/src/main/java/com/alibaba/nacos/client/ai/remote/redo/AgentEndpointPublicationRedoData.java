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

package com.alibaba.nacos.client.ai.remote.redo;

import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.redo.data.RedoData;

/**
 * Redo state for one complete protocol-neutral Agent Endpoint publication.
 *
 * @author Nacos
 */
public class AgentEndpointPublicationRedoData
    extends RedoData<AgentEndpointRegistrationBatch> {
    
    private static final String KEY_SEPARATOR = Constants.SERVICE_INFO_SPLITER;
    
    private final String key;
    
    private final String namespaceId;
    
    /**
     * Build redo state from one complete publication Batch.
     *
     * <p>The internal redo key is generated from the publication identity
     * {@code (namespaceId, agentName, protocol)} and reuses Naming's readable
     * {@code @@} separator. Validated namespaces and protocols reject {@code @}, so the first and
     * last separator boundaries remain unambiguous even when an Agent name contains it.</p>
     *
     * @param namespaceId effective publication namespace
     * @param batch complete publication Batch
     */
    public AgentEndpointPublicationRedoData(String namespaceId,
        AgentEndpointRegistrationBatch batch) {
        this.namespaceId = namespaceId;
        this.key = keyOf(namespaceId, batch.getAgentName(), batch.getProtocol());
        set(batch);
    }
    
    /**
     * Generate the String key required by the shared redo map.
     *
     * @param namespaceId effective namespace
     * @param agentName Agent name
     * @param protocol protocol token
     * @return readable publication key
     */
    public static String keyOf(String namespaceId, String agentName, String protocol) {
        return namespaceId + KEY_SEPARATOR + agentName + KEY_SEPARATOR + protocol;
    }
    
    /**
     * Return the generated publication key.
     *
     * @return publication key
     */
    public String getKey() {
        return key;
    }
    
    /**
     * Return the effective publication namespace retained for replay.
     * @return publication namespace
     */
    public String getNamespaceId() {
        return namespaceId;
    }
    
    @Override
    public boolean equals(Object other) {
        return super.equals(other)
            && key.equals(((AgentEndpointPublicationRedoData) other).key);
    }
    
    @Override
    public int hashCode() {
        return 31 * super.hashCode() + key.hashCode();
    }
}
