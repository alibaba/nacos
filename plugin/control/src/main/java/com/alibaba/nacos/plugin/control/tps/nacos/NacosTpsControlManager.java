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

package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.TpsBarrierCreatorProxy;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.TpsMetrics;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * nacos tps control manager.
 *
 * @author shiyiyue
 */
public class NacosTpsControlManager extends TpsControlManager {
    
    /**
     * point name -> tps barrier.
     */
    protected final Map<String, TpsBarrier> points = new ConcurrentHashMap<>(16);
    
    /**
     * point name -> tps control rule.
     */
    protected final Map<String, TpsControlRule> rules = new ConcurrentHashMap<>(16);
    
    protected ScheduledExecutorService executorService;
    
    public NacosTpsControlManager() {
        executorService = ExecutorFactory.newSingleScheduledExecutorService(r -> {
            Thread thread = new Thread(r, "nacos.plugin.tps.control.reporter");
            thread.setDaemon(true);
            return thread;
        });
        
        startTpsReport();
    }
    
    protected void startTpsReport() {
        executorService
                .scheduleWithFixedDelay(new NacosTpsControlManager.TpsMetricsReporter(), 0, 900, TimeUnit.MILLISECONDS);
    }
    
    /**
     * apple tps rule.
     *
     * @param pointName pointName.
     */
    public synchronized void registerTpsPoint(String pointName) {
        if (!points.containsKey(pointName)) {
            points.put(pointName, TpsBarrierCreatorProxy.getTpsBarrierCreator().createTpsBarrier(pointName));
            if (rules.containsKey(pointName)) {
                points.get(pointName).applyRule(rules.get(pointName));
            } else {
                initTpsRule(pointName);
            }
        }
    }
    
    /**
     * apple tps rule.
     *
     * @param pointName pointName.
     * @param rule      rule.
     */
    public synchronized void applyTpsRule(String pointName, TpsControlRule rule) {
        if (rule == null) {
            rules.remove(pointName);
        } else {
            rules.put(pointName, rule);
        }
        if (points.containsKey(pointName)) {
            points.get(pointName).applyRule(rule);
        }
    }
    
    public Map<String, TpsBarrier> getPoints() {
        return points;
    }
    
    public Map<String, TpsControlRule> getRules() {
        return rules;
    }
    
    /**
     * check tps result.
     *
     * @param tpsRequest TpsRequest.
     * @return check current tps is allowed.
     */
    public TpsCheckResponse check(TpsCheckRequest tpsRequest) {
        
        if (points.containsKey(tpsRequest.getPointName())) {
            try {
                return points.get(tpsRequest.getPointName()).applyTps(tpsRequest);
            } catch (Throwable throwable) {
                Loggers.TPS.warn("[{}]apply tps error,error={}", tpsRequest.getPointName(), throwable);
            }
        }
        return new TpsCheckResponse(true, TpsResultCode.CHECK_SKIP, "skip");
        
    }
    
    class TpsMetricsReporter implements Runnable {
        
        long lastReportSecond = 0L;
        
        /**
         * get format string "2021-01-16 17:20:21" of timestamp.
         *
         * @param timeStamp timestamp milliseconds.
         * @return
         */
        public String getTimeFormatOfSecond(long timeStamp) {
            String format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timeStamp));
            return format;
        }
        
        @Override
        public void run() {
            try {
                long now = System.currentTimeMillis();
                StringBuilder stringBuilder = new StringBuilder();
                Set<Map.Entry<String, TpsBarrier>> entries = points.entrySet();
                
                long tempSecond = 0L;
                
                long metricsTime = now - 1000L;
                String formatString = getTimeFormatOfSecond(metricsTime);
                for (Map.Entry<String, TpsBarrier> entry : entries) {
                    TpsBarrier tpsBarrier = entry.getValue();
                    String pointName = entry.getKey();
                    TpsMetrics metrics = tpsBarrier.getPointBarrier().getMetrics(metricsTime);
                    if (metrics != null) {
                        //already reported.
                        if (lastReportSecond != 0L && lastReportSecond == metrics.getTimeStamp()) {
                            continue;
                        }
                        tempSecond = metrics.getTimeStamp();
                        
                        stringBuilder.append(pointName).append("|").append("point").append("|")
                                .append(metrics.getPeriod()).append("|").append(formatString).append("|")
                                .append(metrics.getCounter().getPassCount()).append("|")
                                .append(metrics.getCounter().getDeniedCount()).append("|").append("\n");
                    }
                }
                
                if (tempSecond > 0) {
                    lastReportSecond = tempSecond;
                }
                
                if (stringBuilder.length() > 0) {
                    Loggers.TPS.info("Tps reporting...\n" + stringBuilder.toString());
                }
            } catch (Throwable throwable) {
                Loggers.TPS.error("Tps reporting error", throwable);
            }
            
        }
    }
    
    @Override
    public String getName() {
        return "nacos";
    }
}
