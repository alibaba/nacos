/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.model.response;

import java.util.Map;

/**
 * Nacos client connection information.
 *
 * @author Nacos
 */
public class ConnectionInfo {
    
    private boolean traced = false;
    
    private Map<String, Boolean> abilityTable;
    
    private ConnectionMetaInfo metaInfo;
    
    public boolean isTraced() {
        return traced;
    }
    
    public void setTraced(boolean traced) {
        this.traced = traced;
    }
    
    public void setAbilityTable(Map<String, Boolean> abilityTable) {
        this.abilityTable = abilityTable;
    }
    
    public Map<String, Boolean> getAbilityTable() {
        return this.abilityTable;
    }
    
    public ConnectionMetaInfo getMetaInfo() {
        return metaInfo;
    }
    
    public void setMetaInfo(ConnectionMetaInfo metaInfo) {
        this.metaInfo = metaInfo;
    }
}

