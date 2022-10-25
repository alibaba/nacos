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

package com.alibaba.nacos.core.remote.control;

import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * tps control manager.
 *
 * @author liuzunfei
 * @version $Id: TpsControlManager.java, v 0.1 2021年01月09日 12:38 PM liuzunfei Exp $
 */
@Service
public class TpsMonitorManager  implements DisposableBean {
    
    public final Map<String, TpsMonitorPoint> points = new ConcurrentHashMap<>(16);
    
    private static ScheduledExecutorService executorService = ExecutorFactory.newSingleScheduledExecutorService(r -> {
        Thread thread = new Thread(r, "nacos.core.remote.tps.control.reporter");
        thread.setDaemon(true);
        return thread;
    });
    
    public TpsMonitorManager() {
        executorService.scheduleWithFixedDelay(new TpsMonitorReporter(), 0, 900, TimeUnit.MILLISECONDS);
    }
    
    /**
     * register point.
     *
     * @param tpsMonitorPoint tps point.
     */
    public void registerTpsControlPoint(TpsMonitorPoint tpsMonitorPoint) {
        Loggers.TPS_CONTROL
                .info("Register tps control,pointName={}, point={} ", tpsMonitorPoint.getPointName(), tpsMonitorPoint);
        points.putIfAbsent(tpsMonitorPoint.getPointName(), tpsMonitorPoint);
        
    }
    
    /**
     * apply tps.
     *
     * @param clientIp  clientIp.
     * @param pointName pointName.
     * @return pass or not.
     */
    public boolean applyTpsForClientIp(String pointName, String connectionId, String clientIp) {
        if (points.containsKey(pointName)) {
            
            return points.get(pointName).applyTps(connectionId, Arrays.asList(new ClientIpMonitorKey(clientIp)));
        }
        return true;
    }
    
    /**
     * apply tps.
     *
     * @param pointName      pointName.
     * @param monitorKeyList monitorKeyList.
     * @return pass or not.
     */
    public boolean applyTps(String pointName, String connectionId, List<MonitorKey> monitorKeyList) {
        if (points.containsKey(pointName)) {
            return points.get(pointName).applyTps(connectionId, monitorKeyList);
        }
        return true;
    }
    
    @Override
    public void destroy() throws Exception {
        if (executorService == null) {
            return;
        }
        ThreadUtils.shutdownThreadPool(executorService);
    }
    
    class TpsMonitorReporter implements Runnable {
        
        long lastReportSecond = 0L;
        
        long lastReportMinutes = 0L;
        
        @Override
        public void run() {
            try {
                long now = System.currentTimeMillis();
                StringBuilder stringBuilder = new StringBuilder();
                Set<Map.Entry<String, TpsMonitorPoint>> entries = points.entrySet();
                
                long tempSecond = 0L;
                long tempMinutes = 0L;
                
                String formatString = TpsMonitorPoint.getTimeFormatOfSecond(now - 1000L);
                for (Map.Entry<String, TpsMonitorPoint> entry : entries) {
                    TpsMonitorPoint value = entry.getValue();
                    //get last second
                    TpsRecorder.TpsSlot pointSlot = value.getTpsRecorder().getPoint(now - 1000L);
                    if (pointSlot == null) {
                        continue;
                    }
                    
                    //already reported.
                    if (lastReportSecond != 0L && lastReportSecond == pointSlot.time) {
                        continue;
                    }
                    String point = entry.getKey();
                    tempSecond = pointSlot.time;
                    stringBuilder.append(point).append('|').append("point|").append(value.getTpsRecorder().period)
                            .append('|').append(formatString).append('|')
                            .append(pointSlot.getCountHolder(point).count.get()).append('|')
                            .append(pointSlot.getCountHolder(point).interceptedCount.get()).append('\n');
                    for (Map.Entry<String, TpsRecorder> monitorKeyEntry : value.monitorKeysRecorder.entrySet()) {
                        String monitorPattern = monitorKeyEntry.getKey();
                        TpsRecorder ipRecord = monitorKeyEntry.getValue();
                        TpsRecorder.TpsSlot keySlot = ipRecord.getPoint(now - ipRecord.period.toMillis(1));
                        if (keySlot == null) {
                            continue;
                        }
                        //already reported.
                        if (ipRecord.period == TimeUnit.SECONDS) {
                            if (lastReportSecond != 0L && lastReportSecond == keySlot.time) {
                                continue;
                            }
                        }
                        if (ipRecord.period == TimeUnit.MINUTES) {
                            if (lastReportMinutes != 0L && lastReportMinutes == keySlot.time) {
                                continue;
                            }
                        }
                        String timeFormatOfSecond = TpsMonitorPoint.getTimeFormatOfSecond(keySlot.time);
                        tempMinutes = keySlot.time;
                        if (ipRecord.isProtoModel()) {
                            Map<String, TpsRecorder.SlotCountHolder> keySlots = ((TpsRecorder.MultiKeyTpsSlot) keySlot).keySlots;
                            for (Map.Entry<String, TpsRecorder.SlotCountHolder> slotCountHolder : keySlots.entrySet()) {
                                stringBuilder.append(point).append('|').append(monitorPattern).append('|')
                                        .append(ipRecord.period).append('|').append(timeFormatOfSecond).append('|')
                                        .append(slotCountHolder.getKey()).append('|')
                                        .append(slotCountHolder.getValue().count).append('|')
                                        .append(slotCountHolder.getValue().interceptedCount).append('\n');
                            }
                            
                        } else {
                            stringBuilder.append(point).append('|').append(monitorPattern).append('|')
                                    .append(ipRecord.period).append('|').append(timeFormatOfSecond).append('|')
                                    .append(keySlot.getCountHolder(point).count.get()).append('|')
                                    .append(keySlot.getCountHolder(point).interceptedCount.get()).append('\n');
                        }
                    }
                }
                
                if (tempSecond > 0) {
                    lastReportSecond = tempSecond;
                }
                if (tempMinutes > 0) {
                    lastReportMinutes = tempMinutes;
                }
                if (stringBuilder.length() > 0) {
                    Loggers.TPS_CONTROL_DIGEST.info("Tps reporting...\n" + stringBuilder.toString());
                }
            } catch (Throwable throwable) {
                Loggers.TPS_CONTROL_DIGEST.error("Tps reporting error", throwable);
                
            }
            
        }
    }
    
}
