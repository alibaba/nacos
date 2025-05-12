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

import com.alibaba.nacos.api.utils.StringUtils;

import java.util.Map;

/**
 * Server loader metric.
 *
 * @author yunye
 * @since 3.0.0-beta
 */
public class ServerLoaderMetric {
    
    private String address;
    
    private int sdkConCount;
    
    private int conCount;
    
    private String load;
    
    private String cpu;
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public int getSdkConCount() {
        return sdkConCount;
    }
    
    public void setSdkConCount(int sdkConCount) {
        this.sdkConCount = sdkConCount;
    }
    
    public int getConCount() {
        return conCount;
    }
    
    public void setConCount(int conCount) {
        this.conCount = conCount;
    }
    
    public String getLoad() {
        return load;
    }
    
    public void setLoad(String load) {
        this.load = load;
    }
    
    public String getCpu() {
        return cpu;
    }
    
    public void setCpu(String cpu) {
        this.cpu = cpu;
    }
    
    public static class Builder {
        
        private ServerLoaderMetric serverLoaderMetric = new ServerLoaderMetric();
        
        public static Builder newBuilder() {
            return new Builder();
        }
        
        public ServerLoaderMetric build() {
            return serverLoaderMetric;
        }
        
        public Builder withAddress(String address) {
            serverLoaderMetric.setAddress(address);
            return this;
        }
        
        /**
         * convert map to {@link ServerLoaderMetric}.
         *
         * @param metric map of server loader metric
         * @return builder
         */
        public Builder convertFromMap(Map<String, String> metric) {
            serverLoaderMetric.setSdkConCount(convertInt(metric, "sdkConCount", 0));
            serverLoaderMetric.setConCount(convertInt(metric, "conCount", 0));
            serverLoaderMetric.setLoad(metric.get("load"));
            serverLoaderMetric.setCpu(metric.get("cpu"));
            return this;
        }
        
        private int convertInt(Map<String, String> metric, String key, int defaultValue) {
            String value = metric.get(key);
            if (!StringUtils.isBlank(value)) {
                return Integer.parseInt(value);
            }
            return defaultValue;
        }
    }
}
