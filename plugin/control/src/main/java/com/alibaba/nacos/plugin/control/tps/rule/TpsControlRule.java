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

package com.alibaba.nacos.plugin.control.tps.rule;

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
    
    private RuleDetail pointRule;
    
    /**
     * rule name,rule detail.
     */
    private Map<String, RuleDetail> monitorKeyRule = new HashMap<>();
    
    public String getPointName() {
        return pointName;
    }
    
    public void setPointName(String pointName) {
        this.pointName = pointName;
    }
    
    public RuleDetail getPointRule() {
        return pointRule;
    }
    
    public void setPointRule(RuleDetail pointRule) {
        this.pointRule = pointRule;
    }
    
    public Map<String, RuleDetail> getMonitorKeyRule() {
        return monitorKeyRule;
    }
    
    public void setMonitorKeyRule(Map<String, RuleDetail> monitorKeyRule) {
        this.monitorKeyRule = monitorKeyRule;
    }
    
    @Override
    public String toString() {
        return "TpsControlRule{" + "pointName='" + pointName + '\'' + ", pointRule=" + pointRule + ", monitorKeyRule="
                + monitorKeyRule + '}';
    }
}
