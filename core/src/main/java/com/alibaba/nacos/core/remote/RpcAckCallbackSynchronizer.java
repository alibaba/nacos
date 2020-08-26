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
    
    private static final Map<String, Map<String, DefaultPushFuture>> CALLBACK_CONTEXT = new ConcurrentLinkedHashMap.Builder<String, Map<String, DefaultPushFuture>>()
            .maximumWeightedCapacity(1000000).listener(new EvictionListener<String, Map<String, DefaultPushFuture>>() {
                @Override
                public void onEviction(String s, Map<String, DefaultPushFuture> pushCallBack) {
                    
                    pushCallBack.entrySet().forEach(new Consumer<Map.Entry<String, DefaultPushFuture>>() {
                        @Override
                        public void accept(Map.Entry<String, DefaultPushFuture> stringDefaultPushFutureEntry) {
                            stringDefaultPushFutureEntry.getValue().setFailResult(new TimeoutException());
                        }
                    });
                }
            }).build();
    
    /**
     * notify  ackid.
     */
    public static void ackNotify(String connectionId, String requestId, boolean success, Exception e) {
    
        Map<String, DefaultPushFuture> stringDefaultPushFutureMap = CALLBACK_CONTEXT.get(connectionId);
        if (stringDefaultPushFutureMap == null) {
            return;
        }
    
        DefaultPushFuture currentCallback = stringDefaultPushFutureMap.get(requestId);
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
            throws NacosException {
        if (!CALLBACK_CONTEXT.containsKey(connectionId)) {
            CALLBACK_CONTEXT.putIfAbsent(connectionId, new HashMap<String, DefaultPushFuture>());
        }
        Map<String, DefaultPushFuture> stringDefaultPushFutureMap = CALLBACK_CONTEXT.get(connectionId);
        if (!stringDefaultPushFutureMap.containsKey(requestId)) {
            DefaultPushFuture pushCallBackPrev = stringDefaultPushFutureMap.putIfAbsent(requestId, defaultPushFuture);
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
    public static void clearFuture(String connetionId, String requestId) {
        Map<String, DefaultPushFuture> stringDefaultPushFutureMap = CALLBACK_CONTEXT.get(connetionId);
        
        if (stringDefaultPushFutureMap == null || !stringDefaultPushFutureMap.containsKey(requestId)) {
            return;
        }
        stringDefaultPushFutureMap.remove(requestId);
    }
    
}

