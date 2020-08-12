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

import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.hessian.clhm.ConcurrentLinkedHashMap;
import com.alipay.hessian.clhm.EvictionListener;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * serber push ack synchronier.
 *
 * @author liuzunfei
 * @version $Id: RpcAckCallbackSynchronizer.java, v 0.1 2020年07月29日 7:56 PM liuzunfei Exp $
 */
public class RpcAckCallbackSynchronizer {
    
    private static final long TIMEOUT = 60000L;
    
    static ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    
    private static final Map<String, DefaultPushFuture> CALLBACK_CONTEXT = new ConcurrentLinkedHashMap.Builder<String, DefaultPushFuture>()
            .maximumWeightedCapacity(30000).listener(new EvictionListener<String, DefaultPushFuture>() {
                @Override
                public void onEviction(String s, DefaultPushFuture pushCallBack) {
                    if (System.currentTimeMillis() - pushCallBack.getTimeStamp() > TIMEOUT) {
                        Loggers.CORE.warn("time out on eviction:" + pushCallBack.getRequestId());
                        if (pushCallBack.getPushCallBack() != null) {
                            pushCallBack.getPushCallBack().onTimeout();
                        }
                    } else {
                        pushCallBack.getPushCallBack().onFail(new RuntimeException("callback pool overlimit"));
                    }
                }
            }).build();
    
    static {
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                Set<String> timeOutCalls = new HashSet<>();
                long now = System.currentTimeMillis();
                for (Map.Entry<String, DefaultPushFuture> enrty : CALLBACK_CONTEXT.entrySet()) {
                    if (now - enrty.getValue().getTimeStamp() > TIMEOUT) {
                        timeOutCalls.add(enrty.getKey());
                    }
                }
                for (String ackId : timeOutCalls) {
                    DefaultPushFuture remove = CALLBACK_CONTEXT.remove(ackId);
                    if (remove != null) {
                        Loggers.CORE.warn("time out on scheduler:" + ackId);
                        if (remove.getPushCallBack() != null) {
                            remove.getPushCallBack().onTimeout();
                        }
                    }
                }
            }
        }, TIMEOUT, TIMEOUT, TimeUnit.MILLISECONDS);
    }
    
    /**
     * notify  ackid.
     */
    public static void ackNotify(String connectionId, String requestId, boolean success, Exception e) {
    
        DefaultPushFuture currentCallback = CALLBACK_CONTEXT.remove(requestId);
        if (currentCallback == null) {
            return;
        }
    
        if (success) {
            currentCallback.setSuccessResult();
        } else {
            currentCallback.setFailResult(e);
        }
    }
    
    /**
     * notify  ackid.
     */
    public static void syncCallback(String connectionId, String requestId, DefaultPushFuture defaultPushFuture)
            throws Exception {
        DefaultPushFuture pushCallBackPrev = CALLBACK_CONTEXT.putIfAbsent(requestId, defaultPushFuture);
        if (pushCallBackPrev != null) {
            throw new RuntimeException("callback conflict.");
        }
    }
    
    /**
     * clear context of connectionId.
     *
     * @param connetionId connetionId
     */
    public static void clearContext(String connetionId) {
    
    }
    
    /**
     * clear context of connectionId. TODO
     *
     * @param connetionId connetionId
     */
    public static void clearFuture(String connetionId, String requestId) {
    
    }
    
    
}

