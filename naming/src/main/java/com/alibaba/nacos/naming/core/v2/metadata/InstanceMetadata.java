/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.metadata;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service instance metadata for v2.
 *
 * @author xiweng.yy
 */
public class InstanceMetadata implements Serializable {
    
    private static final long serialVersionUID = -8477858617353459226L;
    
    /**
     * instance weight.
     */
    private double weight = 1.0D;
    
    /**
     * If instance is enabled to accept request.
     */
    private boolean enabled = true;
    
    private Map<String, Object> extendData = new ConcurrentHashMap<>(1);
    
    public double getWeight() {
        return weight;
    }
    
    public void setWeight(double weight) {
        this.weight = weight;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Map<String, Object> getExtendData() {
        return extendData;
    }
    
    public void setExtendData(Map<String, Object> extendData) {
        this.extendData = extendData;
    }
}
