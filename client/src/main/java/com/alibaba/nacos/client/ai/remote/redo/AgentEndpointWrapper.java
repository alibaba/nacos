/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.client.ai.remote.redo;

import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Wrapper of {@link AgentEndpoint} and batched {@link AgentEndpoint}.
 *
 * @author xiweng.yy
 */
public class AgentEndpointWrapper {
    
    private final Collection<AgentEndpoint> data;
    
    private final boolean isBatch;
    
    private AgentEndpointWrapper(Collection<AgentEndpoint> data, boolean isBatch) {
        this.data = copyEndpoints(data);
        this.isBatch = isBatch;
    }
    
    public static AgentEndpointWrapper wrap(AgentEndpoint data) {
        return new AgentEndpointWrapper(Collections.singletonList(data), false);
    }
    
    public static AgentEndpointWrapper wrap(Collection<AgentEndpoint> data) {
        return new AgentEndpointWrapper(data, true);
    }
    
    public boolean isBatch() {
        return isBatch;
    }
    
    public AgentEndpoint getData() {
        if (isBatch) {
            throw new UnsupportedOperationException("Can't get single data from batched data.");
        }
        return data.iterator().next();
    }
    
    public Collection<AgentEndpoint> getBatchData() {
        if (!isBatch) {
            throw new UnsupportedOperationException("Can't get batched data from single data.");
        }
        return data;
    }
    
    /**
     * Return the exact Agent Version shared by this wrapper's Endpoint payload.
     *
     * @return exact Version
     */
    public String getVersion() {
        return data.iterator().next().getVersion();
    }
    
    private static Collection<AgentEndpoint> copyEndpoints(Collection<AgentEndpoint> source) {
        List<AgentEndpoint> result = new ArrayList<AgentEndpoint>(source.size());
        for (AgentEndpoint endpoint : source) {
            result.add(copyEndpoint(endpoint));
        }
        return Collections.unmodifiableList(result);
    }
    
    private static AgentEndpoint copyEndpoint(AgentEndpoint source) {
        AgentEndpoint result = new AgentEndpoint();
        result.setTransport(source.getTransport());
        result.setAddress(source.getAddress());
        result.setPort(source.getPort());
        result.setPath(source.getPath());
        result.setSupportTls(source.isSupportTls());
        result.setVersion(source.getVersion());
        result.setProtocolVersion(source.getProtocolVersion());
        result.setTenant(source.getTenant());
        result.setProtocol(source.getProtocol());
        result.setQuery(source.getQuery());
        return result;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentEndpointWrapper that = (AgentEndpointWrapper) o;
        return isBatch == that.isBatch && Objects.equals(data, that.data);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(data, isBatch);
    }
}
