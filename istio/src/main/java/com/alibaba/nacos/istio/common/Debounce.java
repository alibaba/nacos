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
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.model.DeltaResources;
import com.alibaba.nacos.istio.model.PushChange;

import java.util.Date;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

/**.
 * @author RocketEngine26
 * @date 2022/8/20 9:05
 */
public class Debounce implements Callable<DeltaResources> {
    private Date startDebounce;
    
    private Date lastConfigUpdateTime;
    
    private final IstioConfig istioConfig = new IstioConfig();
    
    private final Queue<PushChange> pushChangeQueue;
    
    private final DeltaResources deltaResources = new DeltaResources();
    
    private int debouncedEvents = 0;
    
    private boolean free = true;
    
    private boolean flag = false;
    
    public Debounce(Queue<PushChange> pushChangeQueue) {
        this.pushChangeQueue = pushChangeQueue;
    }
    
    @Override
    public DeltaResources call() throws Exception {
        while (true) {
            if (flag) {
                Loggers.MAIN.info("flag true return");
                return deltaResources;
            }
            
            PushChange pushChange = pushChangeQueue.poll();
            Loggers.MAIN.info("debounce: " + pushChange);
            
            if (pushChange != null) {
                lastConfigUpdateTime = new Date();
                if (debouncedEvents == 0) {
                    startDebounce = lastConfigUpdateTime;
                    Loggers.MAIN.info("debouncedEvents == 0");
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (free) {
                                try {
                                    Loggers.MAIN.info("pushWorker " + debouncedEvents);
                                    pushWorker();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }, istioConfig.getDebounceAfter());
                }
    
                Loggers.MAIN.info("debouncedEvents: {}", debouncedEvents);
                debouncedEvents++;
    
                merge(pushChange);
            }
        }
    }
    
    private void pushWorker() {
        long eventDelay = System.currentTimeMillis() - startDebounce.getTime();
        long quietTime = System.currentTimeMillis() - lastConfigUpdateTime.getTime();
        
        Loggers.MAIN.info("{eventDelay:{} quietTime:{} debouncedEvents:{}}", eventDelay, quietTime, debouncedEvents);
        
        if (eventDelay > istioConfig.getDebounceMax() || quietTime > istioConfig.getDebounceAfter()) {
            if (deltaResources != null) {
                free = false;
                flag = true;
                Loggers.MAIN.info("flag already true");
                debouncedEvents = 0;
            }
        } else {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    if (free) {
                        try {
                            Loggers.MAIN.info("second pushWorker " + debouncedEvents);
                            pushWorker();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }, istioConfig.getDebounceAfter() - quietTime);
        }
    }
    
    private void merge(PushChange pushChange) {
        String[] name = pushChange.getName().split("\\.", 2);
        PushChange.ChangeType changeType = pushChange.getChangeType();
    
        deltaResources.putChangeType(name[0], name[1], changeType);
        Loggers.MAIN.info("merge back");
    }
}
