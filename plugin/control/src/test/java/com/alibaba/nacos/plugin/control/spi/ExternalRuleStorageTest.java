/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.spi;

import com.alibaba.nacos.plugin.control.rule.storage.ExternalRuleStorage;

import java.util.HashMap;
import java.util.Map;

public class ExternalRuleStorageTest implements ExternalRuleStorage {
    
    private String ruleContent;
    
    private Map<String, String> tpsRuleMap = new HashMap<>(1);
    
    @Override
    public String getName() {
        return "testExternal";
    }
    
    @Override
    public void saveConnectionRule(String ruleContent) throws Exception {
        this.ruleContent = ruleContent;
    }
    
    @Override
    public String getConnectionRule() {
        return ruleContent;
    }
    
    @Override
    public void saveTpsRule(String pointName, String ruleContent) throws Exception {
        tpsRuleMap.put(pointName, ruleContent);
    }
    
    @Override
    public String getTpsRule(String pointName) {
        return tpsRuleMap.get(pointName);
    }
}
