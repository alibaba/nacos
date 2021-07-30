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

package com.alibaba.nacos.api.remote.response;

import java.util.HashMap;
import java.util.Map;

/**
 * server loader info response.
 *
 * @author liuzunfei
 * @version $Id: ServerLoaderInfoResponse.java, v 0.1 2020年09月03日 2:46 PM liuzunfei Exp $
 */
public class ServerLoaderInfoResponse extends Response {
    
    String address;
    
    Map<String, String> loaderMetrics = new HashMap<String, String>();
    
    public String getMetricsValue(String key) {
        return loaderMetrics.get(key);
    }
    
    public void putMetricsValue(String key, String value) {
        this.loaderMetrics.put(key, value);
    }
    
    /**
     * Getter method for property <tt>loaderMetrics</tt>.
     *
     * @return property value of loaderMetrics
     */
    public Map<String, String> getLoaderMetrics() {
        return loaderMetrics;
    }
    
    /**
     * Setter method for property <tt>loaderMetrics</tt>.
     *
     * @param loaderMetrics value to be assigned to property loaderMetrics
     */
    public void setLoaderMetrics(Map<String, String> loaderMetrics) {
        this.loaderMetrics = loaderMetrics;
    }
}