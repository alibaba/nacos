/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.istio.common;

import com.alibaba.nacos.istio.misc.IstioConfig;
import com.alibaba.nacos.istio.model.PushRequest;

import java.util.Date;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

/**.
 * @author RocketEngine26
 * @date 2022/8/20 9:05
 */
public class Debounce implements Callable<PushRequest> {
    private Date startDebounce;
    
    private Date lastConfigUpdateTime;
    
    private final IstioConfig istioConfig;
    
    private final Queue<PushRequest> pushRequestQueue;
    
    private PushRequest pushRequest;
    
    private int debouncedEvents = 0;
    
    private boolean free = true;
    
    private boolean flag = false;
    
    public Debounce(Queue<PushRequest> pushRequestQueue, IstioConfig istioConfig) {
        this.pushRequestQueue = pushRequestQueue;
        this.istioConfig = istioConfig;
    }
    
    @Override
    public PushRequest call() throws Exception {
        while (true) {
            if (flag) {
                return pushRequest;
            }
            
            PushRequest otherRequest = pushRequestQueue.poll();
            
            if (otherRequest != null) {
                lastConfigUpdateTime = new Date();
                if (debouncedEvents == 0) {
                    startDebounce = lastConfigUpdateTime;
                    pushRequest = otherRequest;
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (free) {
                                try {
                                    pushWorker();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }, istioConfig.getDebounceAfter());
                } else {
                    merge(otherRequest);
                }
                debouncedEvents++;
            }
        }
    }
    
    private void pushWorker() {
        long eventDelay = System.currentTimeMillis() - startDebounce.getTime();
        long quietTime = System.currentTimeMillis() - lastConfigUpdateTime.getTime();
        
        if (eventDelay > istioConfig.getDebounceMax() || quietTime > istioConfig.getDebounceAfter()) {
            if (pushRequest != null) {
                free = false;
                flag = true;
                debouncedEvents = 0;
            }
        } else {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    if (free) {
                        try {
                            pushWorker();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }, istioConfig.getDebounceAfter() - quietTime);
        }
    }
    
    private void merge(PushRequest otherRequest) {
        pushRequest.getReason().addAll(otherRequest.getReason());
        pushRequest.setFull(pushRequest.isFull() || otherRequest.isFull());
    }
}
