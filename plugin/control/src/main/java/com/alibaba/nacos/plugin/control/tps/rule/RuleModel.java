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

/**
 * rule model.
 *
 * @author shiyiyue
 */
public enum RuleModel {
    
    /**
     * every single monitor key will be counted as one counter.
     */
    FUZZY("FUZZY", "every single monitor key will be counted as one counter"),
    
    /**
     * every single monitor key will be counted as different counter.
     */
    PROTO("PROTO", "every single monitor key will be counted as different counter");
    
    private String model;
    
    private String desc;
    
    RuleModel(String model, String desc) {
        this.model = model;
        this.desc = desc;
    }
}
