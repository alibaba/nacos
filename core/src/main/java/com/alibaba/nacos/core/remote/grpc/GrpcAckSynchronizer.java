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

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.api.remote.response.PushCallBack;
import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.hessian.clhm.ConcurrentLinkedHashMap;
import com.alipay.hessian.clhm.EvictionListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * serber push ack synchronier.
 *
 * @author liuzunfei
 * @version $Id: GrpcAckSynchronizer.java, v 0.1 2020年07月29日 7:56 PM liuzunfei Exp $
 */
public class GrpcAckSynchronizer {
    
    private static final Map<String, AckWaitor> ACK_WAITORS = new HashMap<String, AckWaitor>();
    
    private static final long TIMEOUT = 60000L;
    
    static ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    
    private static final Map<String, PushCallBackWraper> CALLBACK_CONTEXT = new ConcurrentLinkedHashMap.Builder<String, PushCallBackWraper>()
            .maximumWeightedCapacity(30000).listener(new EvictionListener<String, PushCallBackWraper>() {
                @Override
                public void onEviction(String s, PushCallBackWraper pushCallBack) {
                    if (System.currentTimeMillis() - pushCallBack.getTimeStamp() > TIMEOUT && pushCallBack
                            .tryDeActive()) {
                        Loggers.CORE.warn("time out on eviction:" + pushCallBack.ackId);
                        pushCallBack.getPushCallBack().onTimeout();
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
                for (Map.Entry<String, PushCallBackWraper> enrty : CALLBACK_CONTEXT.entrySet()) {
                    if (now - enrty.getValue().getTimeStamp() > TIMEOUT) {
                        timeOutCalls.add(enrty.getKey());
                    }
                }
                for (String ackId : timeOutCalls) {
                    PushCallBackWraper remove = CALLBACK_CONTEXT.remove(ackId);
                    if (remove != null && remove.tryDeActive()) {
                        Loggers.CORE.warn("time out on scheduler:" + ackId);
                        remove.pushCallBack.onTimeout();
                    }
                }
            }
        }, TIMEOUT, TIMEOUT, TimeUnit.MILLISECONDS);
    }
    
    /**
     * notify  ackid.
     *
     * @param ackId ackId.
     */
    public static void ackNotify(String ackId, boolean success) {
    
        PushCallBackWraper currentCallback = CALLBACK_CONTEXT.remove(ackId);
        if (currentCallback != null && currentCallback.tryDeActive()) {
            if (success) {
                currentCallback.pushCallBack.onSuccess();
            } else {
                currentCallback.pushCallBack.onFail(new RuntimeException("client return fail"));
            }
        }
        
        AckWaitor waiter = ACK_WAITORS.remove(ackId);
        if (waiter != null) {
            synchronized (waiter) {
                waiter.setSuccess(success);
                waiter.notify();
            }
        }
        
    }
    
    /**
     * notify  ackid.
     *
     * @param ackId ackId.
     */
    public static void release(String ackId) {
        ACK_WAITORS.remove(ackId);
    }
    
    /**
     * notify  ackid.
     *
     * @param ackId ackId.
     */
    public static boolean waitAck(String ackId, long timeout) throws Exception {
        AckWaitor waiter = ACK_WAITORS.get(ackId);
        if (waiter != null) {
            throw new RuntimeException("ackid conflict");
        } else {
            AckWaitor lock = new AckWaitor();
            AckWaitor prev = ACK_WAITORS.putIfAbsent(ackId, lock);
            if (prev == null) {
                synchronized (lock) {
                    lock.wait(timeout);
                    return lock.success;
                }
            } else {
                throw new RuntimeException("ackid conflict.");
            }
        }
    }
    
    /**
     * notify  ackid.
     *
     * @param ackId ackId.
     */
    public static void syncCallbackOnAck(String ackId, PushCallBack pushCallBack) throws Exception {
        PushCallBackWraper pushCallBackPrev = CALLBACK_CONTEXT
                .putIfAbsent(ackId, new PushCallBackWraper(pushCallBack, ackId));
        if (pushCallBackPrev != null) {
            throw new RuntimeException("callback conflict.");
        }
    }
    
    static class AckWaitor {
        
        boolean success;
        
        /**
         * Getter method for property <tt>success</tt>.
         *
         * @return property value of success
         */
        public boolean isSuccess() {
            return success;
        }
        
        /**
         * Setter method for property <tt>success</tt>.
         *
         * @param success value to be assigned to property success
         */
        public void setSuccess(boolean success) {
            this.success = success;
        }
    }
    
    static class PushCallBackWraper {
        
        long timeStamp;
        
        PushCallBack pushCallBack;
        
        String ackId;
        
        private AtomicBoolean active = new AtomicBoolean(true);
        
        public PushCallBackWraper(PushCallBack pushCallBack, String ackId) {
            this.pushCallBack = pushCallBack;
            this.ackId = ackId;
            this.timeStamp = System.currentTimeMillis();
        }
        
        public boolean tryDeActive() {
            return active.compareAndSet(true, false);
        }
        
        /**
         * Getter method for property <tt>timeStamp</tt>.
         *
         * @return property value of timeStamp
         */
        public long getTimeStamp() {
            return timeStamp;
        }
        
        /**
         * Getter method for property <tt>pushCallBack</tt>.
         *
         * @return property value of pushCallBack
         */
        public PushCallBack getPushCallBack() {
            return pushCallBack;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PushCallBackWraper that = (PushCallBackWraper) o;
            return Objects.equals(ackId, that.ackId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(ackId);
        }
    }
    
}

