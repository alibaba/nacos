/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.model.core;

import java.util.HashMap;
import java.util.Map;

public class ServerLoaderMetrics {
    
    String address;
    
    Map<String, String> metric = new HashMap<>();
    
    /**
     * Getter method for property <tt>address</tt>.
     *
     * @return property value of address
     */
    public String getAddress() {
        return address;
    }
    
    /**
     * Setter method for property <tt>address</tt>.
     *
     * @param address value to be assigned to property address
     */
    public void setAddress(String address) {
        this.address = address;
    }
    
    /**
     * Getter method for property <tt>metric</tt>.
     *
     * @return property value of metric
     */
    public Map<String, String> getMetric() {
        return metric;
    }
    
    /**
     * Setter method for property <tt>metric</tt>.
     *
     * @param metric value to be assigned to property metric
     */
    public void setMetric(Map<String, String> metric) {
        this.metric = metric;
    }
}