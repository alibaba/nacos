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
import com.alipay.hessian.clhm.ConcurrentLinkedHashMap;
import com.alipay.hessian.clhm.EvictionListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * server push ack synchronier.
 *
 * @author liuzunfei
 * @version $Id: RpcAckCallbackSynchronizer.java, v 0.1 2020年07月29日 7:56 PM liuzunfei Exp $
 */
public class RpcAckCallbackSynchronizer {
    
    @SuppressWarnings("checkstyle:linelength")
    public static final Map<String, Map<String, DefaultRequestFuture>> CALLBACK_CONTEXT = new ConcurrentLinkedHashMap.Builder<String, Map<String, DefaultRequestFuture>>()
            .maximumWeightedCapacity(1000000)
            .listener(new EvictionListener<String, Map<String, DefaultRequestFuture>>() {
                @Override
                public void onEviction(String s, Map<String, DefaultRequestFuture> pushCallBack) {
                    
                    pushCallBack.entrySet().forEach(new Consumer<Map.Entry<String, DefaultRequestFuture>>() {
                        @Override
                        public void accept(Map.Entry<String, DefaultRequestFuture> stringDefaultPushFutureEntry) {
                            stringDefaultPushFutureEntry.getValue().setFailResult(new TimeoutException());
                        }
                    });
                }
            }).build();
    
    /**
     * notify  ackid.
     */
    public static void ackNotify(String connectionId, Response response) {
        
        Map<String, DefaultRequestFuture> stringDefaultPushFutureMap = CALLBACK_CONTEXT.get(connectionId);
        if (stringDefaultPushFutureMap == null) {
            return;
        }
        
        DefaultRequestFuture currentCallback = stringDefaultPushFutureMap.remove(response.getRequestId());
        if (currentCallback == null) {
            return;
        }
        
        if (response.isSuccess()) {
            currentCallback.setResponse(response);
        } else {
            currentCallback.setFailResult(new NacosException(response.getErrorCode(), response.getMessage()));
        }
    }
    
    /**
     * notify  ackid.
     */
    public static void exceptionNotify(String connectionId, String requestId, Exception e) {
        
        Map<String, DefaultRequestFuture> stringDefaultPushFutureMap = CALLBACK_CONTEXT.get(connectionId);
        if (stringDefaultPushFutureMap == null) {
            return;
        }
        
        DefaultRequestFuture currentCallback = stringDefaultPushFutureMap.remove(requestId);
        if (currentCallback == null) {
            return;
        }
        currentCallback.setFailResult(e);
    }
    
    /**
     * notify  ackid.
     */
    public static void syncCallback(String connectionId, String requestId, DefaultRequestFuture defaultPushFuture)
            throws NacosException {
        
        Map<String, DefaultRequestFuture> stringDefaultPushFutureMap = initContextIfNecessary(connectionId);
        ;
        if (!stringDefaultPushFutureMap.containsKey(requestId)) {
            DefaultRequestFuture pushCallBackPrev = stringDefaultPushFutureMap
                    .putIfAbsent(requestId, defaultPushFuture);
            if (pushCallBackPrev == null) {
                return;
            }
        }
        throw new NacosException(NacosException.INVALID_PARAM, "request id confilict");
        
    }
    
    /**
     * clear context of connectionId.
     *
     * @param connetionId connetionId
     */
    public static void clearContext(String connetionId) {
        CALLBACK_CONTEXT.remove(connetionId);
    }
    
    /**
     * clear context of connectionId.
     *
     * @param connetionId connetionId
     */
    public static Map<String, DefaultRequestFuture> initContextIfNecessary(String connetionId) {
        if (!CALLBACK_CONTEXT.containsKey(connetionId)) {
            Map<String, DefaultRequestFuture> context = new HashMap<String, DefaultRequestFuture>(128);
            Map<String, DefaultRequestFuture> stringDefaultRequestFutureMap = CALLBACK_CONTEXT
                    .putIfAbsent(connetionId, context);
            return stringDefaultRequestFutureMap == null ? context : stringDefaultRequestFutureMap;
        } else {
            return CALLBACK_CONTEXT.get(connetionId);
        }
    }
    
    /**
     * clear context of connectionId.
     *
     * @param connetionId connetionId
     */
    public static void clearFuture(String connetionId, String requestId) {
        Map<String, DefaultRequestFuture> stringDefaultPushFutureMap = CALLBACK_CONTEXT.get(connetionId);
        
        if (stringDefaultPushFutureMap == null || !stringDefaultPushFutureMap.containsKey(requestId)) {
            return;
        }
        stringDefaultPushFutureMap.remove(requestId);
    }
    
}

