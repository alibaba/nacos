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

/**
 * tps control point.
 *
 * @author liuzunfei
 * @version $Id: TpsControlPoint.java, v 0.1 2021年01月09日 12:38 PM liuzunfei Exp $
 */
public class TpsControlRule {
    
    private String pointName;
    
    private Rule pointRule;
    
    private Map<String, Rule> ipRule = new HashMap<String, Rule>();
    
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
    
    public Map<String, Rule> getIpRule() {
        return ipRule;
    }
    
    public void setIpRule(Map<String, Rule> ipRule) {
        this.ipRule = ipRule;
    }
    
    public static class Rule {
        
        long maxTps = -1;
        
        /**
         * monitor/intercept.
         */
        String monitorType = "";
        
        public Rule(long maxTps, String monitorType) {
            this.maxTps = maxTps;
            this.monitorType = monitorType;
        }
        
        @Override
        public String toString() {
            return "Rule{" + "maxTps=" + maxTps + ", monitorType='" + monitorType + '\'' + '}';
        }
    }
    
    @Override
    public String toString() {
        return "TpsControlRule{" + "pointName='" + pointName + '\'' + ", pointRule=" + pointRule + ", ipRule=" + ipRule
                + '}';
    }
}
