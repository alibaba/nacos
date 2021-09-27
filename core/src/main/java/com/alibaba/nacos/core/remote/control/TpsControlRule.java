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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * tps control point.
 *
 * @author liuzunfei
 * @version $Id: TpsControlPoint.java, v 0.1 2021年01月09日 12:38 PM liuzunfei Exp $
 */
public class TpsControlRule {
    
    private String pointName;
    
    private Rule pointRule;
    
    /**
     * Pattern,Rule map.
     */
    private Map<String, Rule> monitorKeyRule = new HashMap<String, Rule>();
    
    public String getPointName() {
        return pointName;
    }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
    
    public Rule getPointRule() {
        return pointRule;
    }
    
    public void setPointRule(Rule pointRule) {
        this.pointRule = pointRule;
    }
    
    public Map<String, Rule> getMonitorKeyRule() {
        return monitorKeyRule;
    }
    
    public void setMonitorKeyRule(Map<String, Rule> monitorKeyRule) {
        this.monitorKeyRule = monitorKeyRule;
    }
    
    public static class Rule {
        
        long maxCount = -1;
        
        TimeUnit period = TimeUnit.SECONDS;
        
        public static final String MODEL_FUZZY = "FUZZY";
        
        public static final String MODEL_PROTO = "PROTO";
        
        String model = MODEL_FUZZY;
        
        /**
         * monitor/intercept.
         */
        String monitorType = "";
        
        public Rule() {
        
        }
        
        public boolean isFuzzyModel() {
            return MODEL_FUZZY.equalsIgnoreCase(model);
        }
        
        public boolean isProtoModel() {
            return MODEL_PROTO.equalsIgnoreCase(model);
        }
        
        public Rule(long maxCount, TimeUnit period, String model, String monitorType) {
            this.maxCount = maxCount;
            this.period = period;
            this.model = model;
            this.monitorType = monitorType;
        }
        
        public String getModel() {
            return model;
        }
        
        public void setModel(String model) {
            this.model = model;
        }
        
        public TimeUnit getPeriod() {
            return period;
        }
        
        public void setPeriod(TimeUnit period) {
            this.period = period;
        }
        
        public long getMaxCount() {
            return maxCount;
        }
        
        public void setMaxCount(long maxCount) {
            this.maxCount = maxCount;
        }
        
        public String getMonitorType() {
            return monitorType;
        }
        
        public void setMonitorType(String monitorType) {
            this.monitorType = monitorType;
        }
        
        @Override
        public String toString() {
            return "Rule{" + "maxTps=" + maxCount + ", monitorType='" + monitorType + '\'' + '}';
        }
    }
    
    @Override
    public String toString() {
        return "TpsControlRule{" + "pointName='" + pointName + '\'' + ", pointRule=" + pointRule + ", monitorKeyRule="
                + monitorKeyRule + '}';
    }
}
