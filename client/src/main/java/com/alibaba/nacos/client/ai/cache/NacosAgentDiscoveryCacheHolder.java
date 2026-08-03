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

package com.alibaba.nacos.client.ai.cache;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.client.ai.remote.AiClientProxy;
import com.alibaba.nacos.client.ai.utils.AgentModelUtils;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Local Discover polling subscriptions for protocol-neutral Agents.
 *
 * @author Nacos
 */
public class NacosAgentDiscoveryCacheHolder implements Closeable {
    
    private static final Logger LOGGER =
        LogUtils.logger(NacosAgentDiscoveryCacheHolder.class);
    
    private final String namespaceId;
    
    private final AiClientProxy clientProxy;
    
    private final long updateIntervalMillis;
    
    private final ScheduledExecutorService pollingExecutor;
    
    private final ExecutorService callbackExecutor;
    
    private final Map<SubscriptionKey, Subscription> subscriptions =
        new HashMap<SubscriptionKey, Subscription>();
    
    private boolean closed;
    
    public NacosAgentDiscoveryCacheHolder(String namespaceId, AiClientProxy clientProxy) {
        this(namespaceId, clientProxy, AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL,
            new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.client.ai.agent.discovery")),
            Executors.newCachedThreadPool(
                new NameThreadFactory("com.alibaba.nacos.client.ai.agent.listener")));
    }
    
    NacosAgentDiscoveryCacheHolder(String namespaceId, AiClientProxy clientProxy,
        long updateIntervalMillis, ScheduledExecutorService pollingExecutor,
        ExecutorService callbackExecutor) {
        this.namespaceId = namespaceId;
        this.clientProxy = clientProxy;
        this.updateIntervalMillis = updateIntervalMillis;
        this.pollingExecutor = pollingExecutor;
        this.callbackExecutor = callbackExecutor;
    }
    
    /**
     * Subscribe by periodically executing the same Discover request.
     *
     * @param reference Agent reference
     * @param filter optional filter
     * @param listener listener identity
     * @return current snapshot, or {@code null} when absent
     * @throws NacosException when validation or the initial Discover fails
     */
    public synchronized AgentDiscoveryResult subscribe(AgentReference reference,
        AgentDiscoveryFilter filter, AbstractNacosAgentDiscoveryListener listener)
        throws NacosException {
        if (listener == null) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "AbstractNacosAgentDiscoveryListener must not be null.");
        }
        AgentDiscoveryRequest request =
            AgentModelUtils.copyDiscoveryRequest(reference, filter, namespaceId);
        SubscriptionKey key = new SubscriptionKey(buildRequestKey(request), listener);
        Subscription existing = subscriptions.get(key);
        if (existing != null) {
            return AgentModelUtils.copyDiscoveryResult(existing.current);
        }
        AgentDiscoveryResult current = discoverOrNull(request);
        Subscription subscription = new Subscription(key, request, listener, current);
        subscriptions.put(key, subscription);
        schedule(subscription);
        return AgentModelUtils.copyDiscoveryResult(current);
    }
    
    /**
     * Remove one exact local polling subscription.
     *
     * @param reference Agent reference
     * @param filter optional filter
     * @param listener listener identity
     * @throws NacosException when the reference or filter is invalid
     */
    public synchronized void unsubscribe(AgentReference reference, AgentDiscoveryFilter filter,
        AbstractNacosAgentDiscoveryListener listener) throws NacosException {
        AgentDiscoveryRequest request =
            AgentModelUtils.copyDiscoveryRequest(reference, filter, namespaceId);
        if (listener == null) {
            return;
        }
        Subscription removed =
            subscriptions.remove(new SubscriptionKey(buildRequestKey(request), listener));
        if (removed != null) {
            removed.cancel();
        }
    }
    
    private AgentDiscoveryResult discoverOrNull(AgentDiscoveryRequest request)
        throws NacosException {
        try {
            return clientProxy.discoverAgent(request);
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }
    
    private void schedule(Subscription subscription) {
        if (!closed) {
            subscription.future = pollingExecutor.schedule(subscription, updateIntervalMillis,
                TimeUnit.MILLISECONDS);
        }
    }
    
    private void poll(Subscription subscription) {
        AgentDiscoveryResult latest;
        try {
            latest = discoverOrNull(subscription.request);
        } catch (NacosException e) {
            LOGGER.warn("Agent Discover polling failed.", e);
            rescheduleIfActive(subscription);
            return;
        }
        boolean changed = false;
        synchronized (this) {
            if (closed || subscriptions.get(subscription.key) != subscription) {
                return;
            }
            if (latest == null) {
                subscription.present = false;
            } else {
                String latestFingerprint = fingerprint(latest);
                changed = !subscription.present
                    || !latestFingerprint.equals(subscription.fingerprint);
                subscription.present = true;
                subscription.fingerprint = latestFingerprint;
                subscription.current = AgentModelUtils.copyDiscoveryResult(latest);
            }
            schedule(subscription);
        }
        if (changed) {
            dispatch(subscription, AgentModelUtils.copyDiscoveryResult(subscription.current));
        }
    }
    
    private synchronized void rescheduleIfActive(Subscription subscription) {
        if (!closed && subscriptions.get(subscription.key) == subscription) {
            schedule(subscription);
        }
    }
    
    private void dispatch(final Subscription subscription,
        final AgentDiscoveryResult result) {
        Executor executor = subscription.listener.getExecutor();
        if (executor == null) {
            executor = callbackExecutor;
        }
        try {
            executor.execute(new Runnable() {
                
                @Override
                public void run() {
                    try {
                        invokeIfActive(subscription, result);
                    } catch (Throwable throwable) {
                        LOGGER.warn("Agent discovery listener failed.", throwable);
                    }
                }
            });
        } catch (RuntimeException e) {
            LOGGER.warn("Agent discovery listener dispatch failed.", e);
        }
    }
    
    private synchronized void invokeIfActive(Subscription subscription,
        AgentDiscoveryResult result) {
        if (!closed && subscriptions.get(subscription.key) == subscription) {
            subscription.listener.onEvent(new NacosAgentDiscoveryEvent(result));
        }
    }
    
    private String buildRequestKey(AgentDiscoveryRequest request) {
        AgentReference reference = request.getReference();
        StringBuilder result = new StringBuilder(request.getNamespaceId()).append('\u0000')
            .append(reference.getAgentName()).append('\u0000')
            .append(value(reference.getVersion())).append('\u0000')
            .append(value(reference.getLabel()));
        AgentDiscoveryFilter filter = request.getFilter();
        if (filter == null) {
            return result.toString();
        }
        result.append('\u0000').append(sorted(filter.getProtocols())).append('\u0000')
            .append(value(filter.getProtocolVersion())).append('\u0000')
            .append(sorted(filter.getTransports())).append('\u0000')
            .append(sortedSources(filter.getEndpointSources())).append('\u0000')
            .append(filter.getMetadataSelector() == null ? "{}"
                : new TreeMap<String, String>(filter.getMetadataSelector()).toString());
        return result.toString();
    }
    
    private String sorted(List<String> values) {
        if (values == null) {
            return "[]";
        }
        List<String> copy = new ArrayList<String>(values);
        Collections.sort(copy);
        return copy.toString();
    }
    
    private String sortedSources(List<EndpointSource> values) {
        if (values == null) {
            return "[]";
        }
        List<String> names = new ArrayList<String>(values.size());
        for (EndpointSource source : values) {
            names.add(source.name());
        }
        Collections.sort(names);
        return names.toString();
    }
    
    private String value(String value) {
        return value == null ? "" : value;
    }
    
    private String fingerprint(AgentDiscoveryResult result) {
        List<String> revisions = new ArrayList<String>();
        if (result.getCallInterfaces() != null) {
            for (AgentDiscoveryCallInterface callInterface : result.getCallInterfaces()) {
                if (callInterface.getEndpointSets() == null) {
                    continue;
                }
                for (EndpointSet endpointSet : callInterface.getEndpointSets()) {
                    revisions.add(callInterface.getProtocol() + ':'
                        + endpointSet.getSource() + ':' + endpointSet.getSourceRevision());
                }
            }
        }
        Collections.sort(revisions);
        return value(result.getVersion()) + '\u0000' + value(result.getContentDigest())
            + '\u0000' + revisions;
    }
    
    @Override
    public synchronized void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        for (Subscription subscription : subscriptions.values()) {
            subscription.cancel();
        }
        subscriptions.clear();
        pollingExecutor.shutdownNow();
        callbackExecutor.shutdownNow();
    }
    
    private final class Subscription implements Runnable {
        
        private final SubscriptionKey key;
        
        private final AgentDiscoveryRequest request;
        
        private final AbstractNacosAgentDiscoveryListener listener;
        
        private AgentDiscoveryResult current;
        
        private String fingerprint;
        
        private boolean present;
        
        private ScheduledFuture<?> future;
        
        private Subscription(SubscriptionKey key, AgentDiscoveryRequest request,
            AbstractNacosAgentDiscoveryListener listener, AgentDiscoveryResult current) {
            this.key = key;
            this.request = request;
            this.listener = listener;
            this.current = AgentModelUtils.copyDiscoveryResult(current);
            this.present = current != null;
            this.fingerprint = current == null ? "" : fingerprint(current);
        }
        
        @Override
        public void run() {
            poll(this);
        }
        
        private void cancel() {
            if (future != null) {
                future.cancel(false);
            }
        }
    }
    
    private static final class SubscriptionKey {
        
        private final String requestKey;
        
        private final AbstractNacosAgentDiscoveryListener listener;
        
        private SubscriptionKey(String requestKey,
            AbstractNacosAgentDiscoveryListener listener) {
            this.requestKey = requestKey;
            this.listener = listener;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SubscriptionKey)) {
                return false;
            }
            SubscriptionKey other = (SubscriptionKey) obj;
            return requestKey.equals(other.requestKey) && listener == other.listener;
        }
        
        @Override
        public int hashCode() {
            return 31 * requestKey.hashCode() + System.identityHashCode(listener);
        }
    }
}
