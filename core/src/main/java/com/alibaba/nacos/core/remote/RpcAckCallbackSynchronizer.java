/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.DefaultRequestFuture;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.core.utils.Loggers;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * server push ack synchronier.
 *
 * @author liuzunfei
 * @version $Id: RpcAckCallbackSynchronizer.java, v 0.1 2020年07月29日 7:56 PM liuzunfei Exp $
 */
public class RpcAckCallbackSynchronizer {
    
    private static final int MAX_CALLBACK_CONTEXT_SIZE = 1000000;
    
    private static final String CAPACITY_EXCEEDED_REASON =
        "RPC_ACK_CALLBACK_CONTEXT_CAPACITY_EXCEEDED";
    
    private static final long CAPACITY_WARN_LOG_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1);
    
    private static final AtomicLong LAST_CAPACITY_WARN_LOG_TIME = new AtomicLong();
    
    private static final ConcurrentMap<String, Map<String, DefaultRequestFuture>> CALLBACK_CONTEXT_STORE =
        new ConcurrentHashMap<>(128);
    
    @SuppressWarnings("checkstyle:linelength")
    public static final Map<String, Map<String, DefaultRequestFuture>> CALLBACK_CONTEXT =
        CALLBACK_CONTEXT_STORE;
    
    /**
     * notify  ack.
     *
     * @param connectionId connectionId
     * @param response     response
     */
    public static void ackNotify(String connectionId, Response response) {
        
        Map<String, DefaultRequestFuture> stringDefaultPushFutureMap =
            CALLBACK_CONTEXT.get(connectionId);
        if (stringDefaultPushFutureMap == null) {
            
            Loggers.REMOTE_DIGEST
                .warn("Ack receive on a outdated connection ,connection id={},requestId={} ",
                    connectionId,
                    response.getRequestId());
            return;
        }
        
        DefaultRequestFuture currentCallback =
            stringDefaultPushFutureMap.remove(response.getRequestId());
        if (currentCallback == null) {
            
            Loggers.REMOTE_DIGEST
                .warn("Ack receive on a outdated request ,connection id={},requestId={} ",
                    connectionId,
                    response.getRequestId());
            return;
        }
        
        if (response.isSuccess()) {
            currentCallback.setResponse(response);
        } else {
            currentCallback
                .setFailResult(new NacosException(response.getErrorCode(), response.getMessage()));
        }
    }
    
    /**
     * sync callback.
     *
     * @param connectionId      connectionId
     * @param requestId         requestId
     * @param defaultPushFuture defaultPushFuture
     * @throws NacosException NacosException
     */
    public static void syncCallback(String connectionId, String requestId,
        DefaultRequestFuture defaultPushFuture)
        throws NacosException {
        
        while (true) {
            Map<String, DefaultRequestFuture> context = initContextIfNecessary(connectionId);
            DefaultRequestFuture previous = context.putIfAbsent(requestId, defaultPushFuture);
            
            if (CALLBACK_CONTEXT_STORE.get(connectionId) == context) {
                if (previous == null) {
                    return;
                }
                throw new NacosException(NacosException.INVALID_PARAM, "request id conflict");
            }
            
            if (previous == null && !context.remove(requestId, defaultPushFuture)) {
                return;
            }
        }
    }
    
    /**
     * clear context of connectionId.
     *
     * @param connectionId connectionId
     */
    public static void clearContext(String connectionId) {
        CALLBACK_CONTEXT.remove(connectionId);
    }
    
    /**
     * init context of connectionId if necessary.
     *
     * @param connectionId connectionId
     */
    public static Map<String, DefaultRequestFuture> initContextIfNecessary(String connectionId) {
        Map<String, DefaultRequestFuture> context = CALLBACK_CONTEXT_STORE.get(connectionId);
        if (context != null) {
            return context;
        }
        Map<String, DefaultRequestFuture> newContext = new ConcurrentHashMap<>(128);
        Map<String, DefaultRequestFuture> existingContext =
            CALLBACK_CONTEXT_STORE.putIfAbsent(connectionId, newContext);
        if (existingContext != null) {
            return existingContext;
        }
        trimCallbackContextIfNecessary();
        return newContext;
    }
    
    /**
     * clear context of requestId.
     *
     * @param connectionId connectionId
     * @param requestId    requestId
     */
    public static void clearFuture(String connectionId, String requestId) {
        Map<String, DefaultRequestFuture> stringDefaultPushFutureMap =
            CALLBACK_CONTEXT.get(connectionId);
        
        if (stringDefaultPushFutureMap == null
            || !stringDefaultPushFutureMap.containsKey(requestId)) {
            return;
        }
        stringDefaultPushFutureMap.remove(requestId);
    }
    
    private static void trimCallbackContextIfNecessary() {
        trimCallbackContextIfNecessary(MAX_CALLBACK_CONTEXT_SIZE);
    }
    
    static void trimCallbackContextIfNecessary(int maxSize) {
        while (CALLBACK_CONTEXT_STORE.size() > maxSize) {
            Iterator<String> iterator = CALLBACK_CONTEXT_STORE.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            
            int sizeBefore = CALLBACK_CONTEXT_STORE.size();
            String connectionId = iterator.next();
            Map<String, DefaultRequestFuture> removed = CALLBACK_CONTEXT_STORE.remove(connectionId);
            int failedFutureCount = failRemovedContext(connectionId, removed, maxSize);
            logCapacityTrimIfNecessary(connectionId, failedFutureCount, sizeBefore,
                CALLBACK_CONTEXT_STORE.size(), maxSize);
        }
    }
    
    private static int failRemovedContext(String connectionId,
        Map<String, DefaultRequestFuture> removed, int maxSize) {
        if (removed == null) {
            return 0;
        }
        
        int failedCount = 0;
        for (Map.Entry<String, DefaultRequestFuture> entry : removed.entrySet()) {
            String requestId = entry.getKey();
            DefaultRequestFuture future = entry.getValue();
            if (removed.remove(requestId, future)) {
                failedCount++;
                try {
                    future.setFailResult(new TimeoutException(CAPACITY_EXCEEDED_REASON
                        + ": RPC ACK future was evicted because callback context capacity was exceeded,"
                        + " connectionId=" + connectionId + ", requestId=" + requestId
                        + ", maxContextSize="
                        + maxSize));
                } catch (Throwable throwable) {
                    Loggers.REMOTE_DIGEST
                        .warn("Failed to notify an evicted RPC ACK future, connectionId={},"
                            + " requestId={}", connectionId, requestId, throwable);
                }
            }
        }
        return failedCount;
    }
    
    private static void logCapacityTrimIfNecessary(String connectionId, int failedFutureCount,
        int contextSizeBefore,
        int contextSizeAfter, int maxSize) {
        if (failedFutureCount > 0 && shouldLogCapacityWarn()) {
            Loggers.REMOTE_DIGEST.warn(CAPACITY_EXCEEDED_REASON
                + ": RPC ACK callback context was evicted because"
                + " capacity was exceeded, connectionId={}, failedFutureCount={}, contextSizeBefore={},"
                + " contextSizeAfter={}, maxContextSize={}", connectionId, failedFutureCount,
                contextSizeBefore, contextSizeAfter, maxSize);
        } else {
            Loggers.REMOTE_DIGEST.debug(CAPACITY_EXCEEDED_REASON
                + ": empty RPC ACK callback context was evicted,"
                + " connectionId={}, contextSizeBefore={}, contextSizeAfter={}, maxContextSize={}",
                connectionId,
                contextSizeBefore, contextSizeAfter, maxSize);
        }
    }
    
    private static boolean shouldLogCapacityWarn() {
        long now = System.currentTimeMillis();
        while (true) {
            long lastWarnTime = LAST_CAPACITY_WARN_LOG_TIME.get();
            if (now - lastWarnTime < CAPACITY_WARN_LOG_INTERVAL_MILLIS) {
                return false;
            }
            if (LAST_CAPACITY_WARN_LOG_TIME.compareAndSet(lastWarnTime, now)) {
                return true;
            }
        }
    }
    
}
