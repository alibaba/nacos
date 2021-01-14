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

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * tps control manager.
 *
 * @author liuzunfei
 * @version $Id: TpsControlManager.java, v 0.1 2021年01月09日 12:38 PM liuzunfei Exp $
 */
@Service
public class TpsControlManager extends Subscriber<TpsControlRuleChangeEvent> {
    
    private final Map<String, TpsControlPoint> points = new ConcurrentHashMap<String, TpsControlPoint>(16);
    
    public TpsControlManager() {
        NotifyCenter.registerToPublisher(TpsControlRuleChangeEvent.class, NotifyCenter.ringBufferSize);
        NotifyCenter.registerSubscriber(this);
        new Thread(new TpsMonitorReportor()).start();
    }
    
    /**
     * register point.
     *
     * @param tpsControlPoint tps point.
     */
    public void registerTpsControlPoint(TpsControlPoint tpsControlPoint) {
        Loggers.TPS_CONTROL
                .info("Register tps control,pointName={}, point={} ", tpsControlPoint.getPointName(), tpsControlPoint);
        points.putIfAbsent(tpsControlPoint.getPointName(), tpsControlPoint);
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
            TpsControlRule tpsControlRule = StringUtils.isBlank(event.ruleContent) ? null
                    : JacksonUtils.toObj(event.ruleContent, TpsControlRule.class);
            if (!points.containsKey(event.getPointName())) {
                Loggers.TPS_CONTROL.info("Tps control rule change event ignore,pointName={} ", event.getPointName());
                return;
            }
            
            points.get(event.getPointName()).applyRule(tpsControlRule);
        } catch (Exception e) {
            Loggers.TPS_CONTROL.warn("Tps control rule parse error ,error={} ", e);
        }
        
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return TpsControlRuleChangeEvent.class;
    }
    
    
    class TpsMonitorReportor implements Runnable {
        
        long lastSecond = 0L;
        
        @Override
        public void run() {
            while (true) {
                try {
                    
                    try {
                        Thread.sleep(900L);
                    } catch (InterruptedException e) {
                        //Do nothing.
                    }
                    long now = System.currentTimeMillis();
                    StringBuilder stringBuilder = new StringBuilder();
                    Set<Map.Entry<String, TpsControlPoint>> entries = points.entrySet();
                    for (Map.Entry<String, TpsControlPoint> entry : entries) {
                        String point = entry.getKey();
                        TpsControlPoint value = entry.getValue();
                        // last second
                        TpsRecorder.TpsSlot pointSlot = value.getTpsRecorder().getPoint(now - 1000L);
                        if (pointSlot == null || (lastSecond != 0L && lastSecond == pointSlot.second)) {
                            break;
                        }
                        stringBuilder.append(point).append("|").append("point|").append(pointSlot.second).append("|")
                                .append(pointSlot.tps).append("\n");
                        for (Map.Entry<String, TpsRecorder> entryIp : value.tpsRecordForIp.entrySet()) {
                            String clientIp = entryIp.getKey();
                            TpsRecorder ipRecord = entryIp.getValue();
                            TpsRecorder.TpsSlot slotIp = ipRecord.getPoint(now - 1000L);
                            if (slotIp == null || (lastSecond != 0L && lastSecond == slotIp.second)) {
                                continue;
                            }
                            stringBuilder.append(point).append("|").append("ip|").append(clientIp).append("|")
                                    .append(pointSlot.second).append("|").append(pointSlot.tps).append("\n");
                        }
                        lastSecond = pointSlot.second;
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
    
}
