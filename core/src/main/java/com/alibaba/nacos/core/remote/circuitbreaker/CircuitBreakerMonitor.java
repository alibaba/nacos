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

package com.alibaba.nacos.core.remote.circuitbreaker;

import com.alibaba.nacos.core.utils.Loggers;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Info class in charge of storing and monitoring current server point status (tps / tps window / network flow etc.)
 * Can be extended for custom implementations
 * TODO: design a generic status implementation that contains necessary fields
 *
 * @author chuzefang
 * @version $Id: MatchMode.java, v 0.1 2021年08月07日 22:50 PM chuzefang Exp $
 */
public abstract class CircuitBreakerMonitor {

    protected String pointName;

    protected long startTime;

    public abstract CircuitBreakerRecorder getPointRecorder();

    public abstract Map<String, CircuitBreakerRecorder> getMonitorKeysRecorder();

    public abstract CircuitBreakerRecorder getCurrentRecorder();

    public abstract Map<String, CircuitBreakerRecorder> getMonitorKeyRecorders();

    public abstract void createAndPutNewRecorder(String key, CircuitBreakerConfig config);

    public void clearAllTpsConfigs() {}

    public void stopAllMonitorClient() {}

    public String getPointName() {
        return pointName;
    }

    public synchronized void applyRule(boolean clearAll, CircuitBreakerConfig newPointConfig,
                                       Map<String, CircuitBreakerConfig> newKeyMonitorConfigs) {

        Loggers.TPS_CONTROL.info("Apply tps control rule parse start,pointName=[{}]  ", this.getPointName());
        CircuitBreakerRecorder currentRecorder = getCurrentRecorder();
        CircuitBreakerConfig currentConfig = currentRecorder.getConfig();

        //1.reset all monitor point for null.
        if (clearAll) {
            Loggers.TPS_CONTROL.info("Clear all tps control config ,pointName=[{}]  ", this.getPointName());
            this.clearAllTpsConfigs();
            this.stopAllMonitorClient();
            return;
        }

        //2.check point rule.
        if (newPointConfig == null) {
            Loggers.TPS_CONTROL.info("Clear point  control rule ,pointName=[{}]  ", this.getPointName());
            currentRecorder.getConfig().setIsActive(false);
        } else {
            Loggers.TPS_CONTROL.info("Update  point  control rule ,pointName=[{}]"
                            + ",original monitorType={}, original monitorType={}, ", this.getPointName(),
                    currentConfig.getMonitorType(), newPointConfig.getMonitorType());
            currentRecorder.setConfig(newPointConfig);
        }

        //3.check monitor key rules.
        // 3.1 clear all monitor keys.
        if (newKeyMonitorConfigs == null || newKeyMonitorConfigs.isEmpty()) {
            Loggers.TPS_CONTROL
                    .info("Clear point  control rule for monitorKeys, pointName=[{}]  ", this.getPointName());
            this.stopAllMonitorClient();
        } else {
            Map<String, CircuitBreakerRecorder> monitorKeysRecorderCurrent = getMonitorKeyRecorders();

            for (Map.Entry<String, CircuitBreakerConfig> newMonitorConfig : newKeyMonitorConfigs.entrySet()) {
                if (newMonitorConfig.getValue() == null) {
                    continue;
                }
                boolean checkPattern = newMonitorConfig.getKey() != null;
                if (!checkPattern) {
                    Loggers.TPS_CONTROL.info("Invalid monitor rule, pointName=[{}] ,monitorRule={} ,Ignore this.",
                            this.getPointName(), newMonitorConfig.getKey());
                    continue;
                }
                CircuitBreakerConfig newConfig = newMonitorConfig.getValue();

                //update rule.
                if (monitorKeysRecorderCurrent.containsKey(newMonitorConfig.getKey())) {
                    CircuitBreakerRecorder curRecorder = monitorKeysRecorderCurrent.get(newMonitorConfig.getKey());
                    CircuitBreakerConfig oldConfig = curRecorder.getConfig();
                    Loggers.TPS_CONTROL
                            .info("Update  point  control rule for client ip ,pointName=[{}],monitorKey=[{}],original monitorType={}, new monitorType={}, ",
                                    this.getPointName(), newMonitorConfig.getKey(), oldConfig.getMonitorType(), newConfig.getMonitorType());

                    if (!Objects.equals(oldConfig.getPeriod(), newConfig.getPeriod()) || !Objects
                            .equals(oldConfig.getModel(), newConfig.getModel())) {
                        createAndPutNewRecorder(newMonitorConfig.getKey(), newConfig);
                    } else {
                        curRecorder.setConfig(newConfig);
                    }

                } else {
                    Loggers.TPS_CONTROL
                            .info("Add  point  control rule for client ip ,pointName=[{}],monitorKey=[{}], new monitorType={}, ",
                                    this.getPointName(), newMonitorConfig.getKey(),
                                    newMonitorConfig.getValue().getMonitorType());
                    // add config & new recorder
                    createAndPutNewRecorder(newMonitorConfig.getKey(), newConfig);
                }
            }

            //delete rule.
            Iterator<Map.Entry<String, CircuitBreakerRecorder>> iteratorCurrent = monitorKeysRecorderCurrent.entrySet().iterator();
            while (iteratorCurrent.hasNext()) {
                Map.Entry<String, CircuitBreakerRecorder> next1 = iteratorCurrent.next();
                if (!newKeyMonitorConfigs.containsKey(next1.getKey())) {
                    Loggers.TPS_CONTROL.info("Delete  point  control rule for pointName=[{}] ,monitorKey=[{}]",
                            this.getPointName(), next1.getKey());
                    // remove config & its related recorder
                    monitorKeysRecorderCurrent.remove(next1.getKey());
                    iteratorCurrent.remove();
                }
            }

        }

    }
}
