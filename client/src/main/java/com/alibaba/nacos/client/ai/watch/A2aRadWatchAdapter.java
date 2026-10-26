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

package com.alibaba.nacos.client.ai.watch;

import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEventType;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.client.ai.utils.A2aRadConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Legacy Card listeners backed by the shared RAD Watch lifecycle and dispatcher.
 *
 * @author Nacos
 */
public class A2aRadWatchAdapter {
    
    private final String namespaceId;
    
    private final AgentWatchManager manager;
    
    private final Map<String, Map<AbstractNacosAgentCardListener, CardListener>> listeners =
        new HashMap<String, Map<AbstractNacosAgentCardListener, CardListener>>();
    
    private boolean closed;
    
    public A2aRadWatchAdapter(String namespaceId, AgentWatchManager manager) {
        this.namespaceId = namespaceId;
        this.manager = manager;
    }
    
    /**
     * Subscribe with the legacy initial callback and missing-target semantics.
     * @param name Agent name
     * @param version exact Version, or blank for latest
     * @param listener listener identity
     * @return current complete Card, or null while unavailable
     * @throws NacosException when validation or activation fails
     */
    public AgentCardDetailInfo subscribe(String name, String version,
        AbstractNacosAgentCardListener listener) throws NacosException {
        if (listener == null) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Agent Card listener must not be null.");
        }
        AgentDiscoveryRequest request =
            A2aRadConverter.discoveryRequest(namespaceId, name, version);
        String key = AgentDiscoveryCanonicalizer.canonicalRequestKey(request);
        CardListener bridge;
        synchronized (this) {
            if (closed) {
                throw new NacosException(NacosException.CLIENT_DISCONNECT,
                    "Agent Card Watch is closed.");
            }
            Map<AbstractNacosAgentCardListener, CardListener> group = listeners.get(key);
            if (group == null) {
                group = new IdentityHashMap<AbstractNacosAgentCardListener, CardListener>();
                listeners.put(key, group);
            }
            bridge = group.get(listener);
            if (bridge == null) {
                bridge = new CardListener(request, listener);
                group.put(listener, bridge);
            }
        }
        try {
            AgentDiscoveryResult snapshot =
                manager.subscribe(request.getReference(), request.getFilter(), bridge);
            if (!bridge.active) {
                manager.unsubscribe(request.getReference(), request.getFilter(), bridge);
                return null;
            }
            AgentCardDetailInfo card = project(snapshot, request);
            manager.notifyCurrent(request.getReference(), request.getFilter(), bridge);
            return card;
        } catch (NacosException | RuntimeException failure) {
            remove(key, listener, bridge);
            manager.unsubscribe(request.getReference(), request.getFilter(), bridge);
            throw failure;
        }
    }
    
    /**
     * Remove a listener locally before best-effort wire cleanup.
     * @param name Agent name
     * @param version exact Version, or blank for latest
     * @param listener original listener instance
     * @throws NacosException when the reference is invalid
     */
    public void unsubscribe(String name, String version, AbstractNacosAgentCardListener listener)
        throws NacosException {
        AgentDiscoveryRequest request =
            A2aRadConverter.discoveryRequest(namespaceId, name, version);
        String key = AgentDiscoveryCanonicalizer.canonicalRequestKey(request);
        CardListener bridge;
        synchronized (this) {
            Map<AbstractNacosAgentCardListener, CardListener> group = listeners.get(key);
            bridge = group == null ? null : group.get(listener);
            remove(key, listener, bridge);
        }
        if (bridge != null) {
            manager.unsubscribe(request.getReference(), request.getFilter(), bridge);
        }
    }
    
    /** Remove only this adapter's listeners; the shared manager owns its own shutdown. */
    public void shutdown() {
        List<CardListener> removed = new ArrayList<CardListener>();
        synchronized (this) {
            closed = true;
            for (Map<AbstractNacosAgentCardListener, CardListener> group : listeners.values()) {
                for (CardListener bridge : group.values()) {
                    bridge.active = false;
                    removed.add(bridge);
                }
            }
            listeners.clear();
        }
        for (CardListener bridge : removed) {
            try {
                manager.unsubscribe(bridge.request.getReference(), bridge.request.getFilter(),
                    bridge);
            } catch (NacosException ignored) {
                // Requests were validated and copied before being retained.
            }
        }
    }
    
    private synchronized void remove(String key, AbstractNacosAgentCardListener listener,
        CardListener expected) {
        if (expected == null) {
            return;
        }
        expected.active = false;
        Map<AbstractNacosAgentCardListener, CardListener> group = listeners.get(key);
        if (group != null && group.get(listener) == expected) {
            group.remove(listener);
            if (group.isEmpty()) {
                listeners.remove(key);
            }
        }
    }
    
    private static AgentCardDetailInfo project(AgentDiscoveryResult snapshot,
        AgentDiscoveryRequest request)
        throws NacosException {
        if (snapshot == null) {
            return null;
        }
        try {
            return A2aRadConverter.project(snapshot, request, null);
        } catch (NacosException failure) {
            if (failure.getErrCode() == NacosException.NOT_FOUND) {
                return null;
            }
            throw failure;
        }
    }
    
    private final class CardListener extends AbstractNacosAgentDiscoveryListener {
        
        private final AgentDiscoveryRequest request;
        
        private final AbstractNacosAgentCardListener listener;
        
        private volatile boolean active = true;
        
        private Map<?, ?> lastCard;
        
        private CardListener(AgentDiscoveryRequest request,
            AbstractNacosAgentCardListener listener) {
            this.request = request;
            this.listener = listener;
        }
        
        @Override
        public Executor getExecutor() {
            return listener.getExecutor();
        }
        
        @Override
        public void onEvent(NacosAgentDiscoveryEvent event) {
            if (!active) {
                return;
            }
            if (event.getType() == NacosAgentDiscoveryEventType.UNAVAILABLE) {
                lastCard = null;
                synchronized (A2aRadWatchAdapter.this) {
                    if (!manager.containsListener(request, this)) {
                        remove(AgentDiscoveryCanonicalizer.canonicalRequestKey(request), listener,
                            this);
                    }
                }
                return;
            }
            AgentCardDetailInfo card;
            try {
                card = project(event.getAgentDiscoveryResult(), request);
            } catch (NacosException failure) {
                lastCard = null;
                return;
            }
            if (card == null) {
                lastCard = null;
                return;
            }
            Map<?, ?> content = JsonUtils.toObj(JsonUtils.toJson(card), Map.class);
            if (!content.equals(lastCard)) {
                lastCard = content;
                listener.onEvent(new NacosAgentCardEvent(card));
            }
        }
    }
}
