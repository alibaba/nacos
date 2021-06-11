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

package com.alibaba.nacos.api.config.remote.request;

import com.alibaba.nacos.api.remote.request.ServerRequest;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * request of config module metrics.
 *
 * @author liuzunfei
 * @version $Id: ClientConfigMetricRequest.java, v 0.1 2020年12月30日 9:05 PM liuzunfei Exp $
 */
public class ClientConfigMetricRequest extends ServerRequest {
    
    private static final String MODULE = "config";
    
    private List<MetricsKey> metricsKeys = new ArrayList<MetricsKey>();
    
    @Override
    public String getModule() {
        return MODULE;
    }
    
    public List<MetricsKey> getMetricsKeys() {
        return metricsKeys;
    }
    
    public void setMetricsKeys(List<MetricsKey> metricsKeys) {
        this.metricsKeys = metricsKeys;
    }
    
    public static class MetricsKey implements Serializable {
        
        String type;
        
        String key;
        
        public static final String CACHE_DATA = "cacheData";
        
        public static final String SNAPSHOT_DATA = "snapshotData";
        
        /**
         * build metrics key.
         *
         * @param type type.
         * @param key  key.
         * @return metric key.
         */
        public static MetricsKey build(String type, String key) {
            MetricsKey metricsKey = new MetricsKey();
            metricsKey.type = type;
            metricsKey.key = key;
            return metricsKey;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getKey() {
            return key;
        }
        
        public void setKey(String key) {
            this.key = key;
        }
        
        @Override
        public String toString() {
            return "MetricsKey{" + "type='" + type + '\'' + ", key='" + key + '\'' + '}';
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MetricsKey that = (MetricsKey) o;
            return Objects.equals(type, that.type) && Objects.equals(key, that.key);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(type, key);
        }
    }
    
}
