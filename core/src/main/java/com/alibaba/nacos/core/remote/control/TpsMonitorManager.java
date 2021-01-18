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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
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
    
    private static ScheduledExecutorService executorService = ExecutorFactory
            .newSingleScheduledExecutorService(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "nacos.core.remote.tps.control.reporter");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    
    public TpsMonitorManager() {
        NotifyCenter.registerToPublisher(TpsControlRuleChangeEvent.class, NotifyCenter.ringBufferSize);
        NotifyCenter.registerSubscriber(this);
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
        try {
            loadRuleFromLocal(tpsMonitorPoint);
        } catch (IOException e) {
            Loggers.TPS_CONTROL
                    .error("Fail to init rule from local,pointName={},error={}", tpsMonitorPoint.getPointName(), e);
        }
        if (points.putIfAbsent(tpsMonitorPoint.getPointName(), tpsMonitorPoint) == null) {
            registerFileWatch(tpsMonitorPoint);
        }
    }
    
    private void registerFileWatch(TpsMonitorPoint tpsMonitorPoint) {
        try {
            String tpsPath = Paths.get(EnvUtil.getNacosHome(), "data" + File.separator + "tps" + File.separator)
                    .toString();
            WatchFileCenter.registerWatcher(tpsPath, new FileWatcher() {
                @Override
                public void onChange(FileChangeEvent event) {
                    try {
                        String fileName = event.getContext().toString();
                        if (points.get(fileName) != null) {
                            loadRuleFromLocal(points.get(fileName));
                        }
                    } catch (Throwable throwable) {
                        Loggers.TPS_CONTROL.warn("Fail to load rule from local,pointName={},error={}",
                                tpsMonitorPoint.getPointName(), throwable);
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
            Loggers.TPS_CONTROL
                    .warn("Register tps point rule fail ,pointName={},error={}", tpsMonitorPoint.getPointName(), e);
        }
    }
    
    /**
     * apply tps.
     *
     * @param clientIp  clientIp.
     * @param pointName pointName.
     * @return pass or not.
     */
    public boolean applyTps(String clientIp, String pointName) {
        if (points.containsKey(pointName)) {
            return points.get(pointName).applyTps(clientIp);
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
            Loggers.TPS_CONTROL.warn("Tps control rule apply error ,error={} ", e);
        }
        
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return TpsControlRuleChangeEvent.class;
    }
    
    class TpsMonitorReporter implements Runnable {
        
        long lastReportSecond = 0L;
        
        @Override
        public void run() {
            try {
                long now = System.currentTimeMillis();
                StringBuilder stringBuilder = new StringBuilder();
                Set<Map.Entry<String, TpsMonitorPoint>> entries = points.entrySet();
                
                long tempSecond = TpsMonitorPoint.getTrimMillsOfSecond(now - 1000L);
                String formatString = TpsMonitorPoint.getTimeFormatOfSecond(tempSecond);
                for (Map.Entry<String, TpsMonitorPoint> entry : entries) {
                    TpsMonitorPoint value = entry.getValue();
                    //get last second
                    TpsRecorder.TpsSlot pointSlot = value.getTpsRecorder().getPoint(now - 1000L);
                    if (pointSlot == null) {
                        continue;
                    }
                    //already reported.
                    if (lastReportSecond != 0L && lastReportSecond == pointSlot.second) {
                        continue;
                    }
                    String point = entry.getKey();
                    tempSecond = pointSlot.second;
                    stringBuilder.append(point).append("|").append("point|").append(formatString).append("|")
                            .append(pointSlot.tps.get()).append("|").append(pointSlot.interceptedTps.get())
                            .append("\n");
                    for (Map.Entry<String, TpsRecorder> entryIp : value.tpsRecordForIp.entrySet()) {
                        String clientIp = entryIp.getKey();
                        TpsRecorder ipRecord = entryIp.getValue();
                        TpsRecorder.TpsSlot slotIp = ipRecord.getPoint(now - 1000L);
                        if (slotIp == null) {
                            continue;
                        }
                        //already reported.
                        if (lastReportSecond != 0L && lastReportSecond == slotIp.second) {
                            continue;
                        }
                        stringBuilder.append(point).append("|").append("ip|").append(clientIp).append("|")
                                .append(formatString).append("|").append(slotIp.tps.get()).append("|")
                                .append(slotIp.interceptedTps.get()).append("\n");
                    }
                }
                
                if (stringBuilder.length() > 0) {
                    lastReportSecond = tempSecond;
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
        File baseDir = new File(EnvUtil.getNacosHome(), "data" + File.separator + "tps" + File.separator);
        if (!baseDir.exists()) {
            baseDir.mkdir();
        }
        File pointFile = new File(baseDir, pointName);
        return pointFile;
    }
}
