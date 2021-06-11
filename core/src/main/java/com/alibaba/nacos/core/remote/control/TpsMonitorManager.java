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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.file.FileChangeEvent;
import com.alibaba.nacos.sys.file.FileWatcher;
import com.alibaba.nacos.sys.file.WatchFileCenter;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
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
public class TpsMonitorManager extends Subscriber<TpsControlRuleChangeEvent> {
    
    public final Map<String, TpsMonitorPoint> points = new ConcurrentHashMap<String, TpsMonitorPoint>(16);
    
    private static ScheduledExecutorService executorService = ExecutorFactory.newSingleScheduledExecutorService(r -> {
        Thread thread = new Thread(r, "nacos.core.remote.tps.control.reporter");
        thread.setDaemon(true);
        return thread;
    });
    
    public TpsMonitorManager() {
        NotifyCenter.registerToPublisher(TpsControlRuleChangeEvent.class, NotifyCenter.ringBufferSize);
        NotifyCenter.registerSubscriber(this);
        executorService.scheduleWithFixedDelay(new TpsMonitorReporter(), 0, 900, TimeUnit.MILLISECONDS);
        registerFileWatch();
    }
    
    /**
     * register point.
     *
     * @param tpsMonitorPoint tps point.
     */
    public void registerTpsControlPoint(TpsMonitorPoint tpsMonitorPoint) {
        Loggers.TPS_CONTROL
                .info("Register tps control,pointName={}, point={} ", tpsMonitorPoint.getPointName(), tpsMonitorPoint);
        try {
            loadRuleFromLocal(tpsMonitorPoint);
        } catch (IOException e) {
            Loggers.TPS_CONTROL
                    .error("Fail to init rule from local,pointName={},error={}", tpsMonitorPoint.getPointName(), e);
        }
        points.putIfAbsent(tpsMonitorPoint.getPointName(), tpsMonitorPoint);
        
    }
    
    private void registerFileWatch() {
        try {
            String tpsPath = Paths.get(EnvUtil.getNacosHome(), "data" + File.separator + "tps" + File.separator)
                    .toString();
            checkBaseDir();
            WatchFileCenter.registerWatcher(tpsPath, new FileWatcher() {
                @Override
                public void onChange(FileChangeEvent event) {
                    String fileName = event.getContext().toString();
                    try {
                        
                        if (points.get(fileName) != null) {
                            loadRuleFromLocal(points.get(fileName));
                        }
                    } catch (Throwable throwable) {
                        Loggers.TPS_CONTROL
                                .warn("Fail to load rule from local,pointName={},error={}", fileName, throwable);
                    }
                }
                
                @Override
                public boolean interest(String context) {
                    for (String pointName : points.keySet()) {
                        if (context.equals(pointName)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        } catch (NacosException e) {
            Loggers.TPS_CONTROL.warn("Register fire watch fail.", e);
        }
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
    public void onEvent(TpsControlRuleChangeEvent event) {
        
        Loggers.TPS_CONTROL
                .info("Tps control rule change event receive,pointName={}, ruleContent={} ", event.getPointName(),
                        event.ruleContent);
        if (event == null || event.getPointName() == null) {
            return;
        }
        try {
            TpsControlRule tpsControlRule = StringUtils.isBlank(event.ruleContent) ? new TpsControlRule()
                    : JacksonUtils.toObj(event.ruleContent, TpsControlRule.class);
            if (!points.containsKey(event.getPointName())) {
                Loggers.TPS_CONTROL.info("Tps control rule change event ignore,pointName={} ", event.getPointName());
                return;
            }
            try {
                saveRuleToLocal(event.getPointName(), tpsControlRule);
            } catch (Throwable throwable) {
                Loggers.TPS_CONTROL
                        .warn("Tps control rule persist fail,pointName={},error={} ", event.getPointName(), throwable);
                
            }
        } catch (Exception e) {
            Loggers.TPS_CONTROL.warn("Tps control rule apply error ,error= ", e);
        }
        
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return TpsControlRuleChangeEvent.class;
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
                    stringBuilder.append(point).append("|").append("point|").append(value.getTpsRecorder().period)
                            .append("|").append(formatString).append("|")
                            .append(pointSlot.getCountHolder(point).count.get()).append("|")
                            .append(pointSlot.getCountHolder(point).interceptedCount.get()).append("\n");
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
                                stringBuilder.append(point).append("|").append(monitorPattern).append("|")
                                        .append(ipRecord.period).append("|").append(timeFormatOfSecond).append("|")
                                        .append(slotCountHolder.getKey()).append("|")
                                        .append(slotCountHolder.getValue().count).append("|")
                                        .append(slotCountHolder.getValue().interceptedCount).append("\n");
                            }
                            
                        } else {
                            stringBuilder.append(point).append("|").append(monitorPattern).append("|")
                                    .append(ipRecord.period).append("|").append(timeFormatOfSecond).append("|")
                                    .append(keySlot.getCountHolder(point).count.get()).append("|")
                                    .append(keySlot.getCountHolder(point).interceptedCount.get()).append("\n");
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
    
    private synchronized void loadRuleFromLocal(TpsMonitorPoint tpsMonitorPoint) throws IOException {
        
        File pointFile = getRuleFile(tpsMonitorPoint.getPointName());
        if (!pointFile.exists()) {
            pointFile.createNewFile();
        }
        String ruleContent = DiskUtils.readFile(pointFile);
        TpsControlRule tpsControlRule = StringUtils.isBlank(ruleContent) ? new TpsControlRule()
                : JacksonUtils.toObj(ruleContent, TpsControlRule.class);
        Loggers.TPS_CONTROL.info("Load rule from local,pointName={}, ruleContent={} ", tpsMonitorPoint.getPointName(),
                ruleContent);
        tpsMonitorPoint.applyRule(tpsControlRule);
        
    }
    
    private synchronized void saveRuleToLocal(String pointName, TpsControlRule tpsControlRule) throws IOException {
        
        File pointFile = getRuleFile(pointName);
        if (!pointFile.exists()) {
            pointFile.createNewFile();
        }
        String content = JacksonUtils.toJson(tpsControlRule);
        DiskUtils.writeFile(pointFile, content.getBytes(Constants.ENCODE), false);
        Loggers.TPS_CONTROL.info("Save rule to local,pointName={}, ruleContent ={} ", pointName, content);
    }
    
    private File getRuleFile(String pointName) {
        File baseDir = checkBaseDir();
        return new File(baseDir, pointName);
    }
    
    private File checkBaseDir() {
        File baseDir = new File(EnvUtil.getNacosHome(), "data" + File.separator + "tps" + File.separator);
        if (!baseDir.exists()) {
            baseDir.mkdir();
        }
        return baseDir;
    }
}
