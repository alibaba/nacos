/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.tps.barrier.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * nacos tps control manager.
 *
 * @author shiyiyue
 */
public class DefaultTpsControlManager extends TpsControlManager {
    
    /**
     * point name -> tps barrier.
     */
    protected final Map<String, TpsBarrier> points = new ConcurrentHashMap<>(16);
    
    /**
     * point name -> tps control rule.
     */
    protected final Map<String, TpsControlRule> rules = new ConcurrentHashMap<>(16);
    
    public DefaultTpsControlManager() {
    }
    
    /**
     * apple tps rule.
     *
     * @param pointName pointName.
     */
    public synchronized void registerTpsPoint(String pointName) {
        if (!points.containsKey(pointName)) {
            points.put(pointName, tpsBarrierCreator.createTpsBarrier(pointName));
            if (rules.containsKey(pointName)) {
                points.get(pointName).applyRule(rules.get(pointName));
            } else {
                initTpsRule(pointName);
            }
        }
        Loggers.CONTROL
                .warn("Tps point for {} registered, But tps control manager is no limit implementation.", pointName);
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
        Loggers.CONTROL.warn("Tps rule for point name {} updated, But tps control manager is no limit implementation.",
                pointName);
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
        return new TpsCheckResponse(true, TpsResultCode.CHECK_SKIP, "skip");
        
    }
    
    @Override
    public String getName() {
        return "noLimit";
    }
}
