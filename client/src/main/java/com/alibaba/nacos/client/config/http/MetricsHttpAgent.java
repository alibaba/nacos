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

package com.alibaba.nacos.client.config.http;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.monitor.MetricsMonitor;
import com.alibaba.nacos.common.http.HttpRestResult;
import io.prometheus.client.Histogram;

import java.util.Map;

/**
 * MetricsHttpAgent.
 *
 * @author Nacos
 */
public class MetricsHttpAgent implements HttpAgent {
    
    private static final String GET = "GET";
    
    private static final String POST = "POST";
    
    private static final String DELETE = "DELETE";
    
    private static final String DEFAULT_CODE = "NA";
    
    private final HttpAgent httpAgent;
    
    public MetricsHttpAgent(HttpAgent httpAgent) {
        this.httpAgent = httpAgent;
    }

    @Override
    public void start() throws NacosException {
        httpAgent.start();
    }
    
    @Override
    public HttpRestResult<String> httpGet(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        Histogram.Timer timer = MetricsMonitor.getConfigRequestMonitor(GET, path, DEFAULT_CODE);
        HttpRestResult<String> result;
        try {
            result = httpAgent.httpGet(path, headers, paramValues, encode, readTimeoutMs);
        } finally {
            timer.observeDuration();
        }
        
        return result;
    }
    
    @Override
    public HttpRestResult<String> httpPost(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        Histogram.Timer timer = MetricsMonitor.getConfigRequestMonitor(POST, path, DEFAULT_CODE);
        HttpRestResult<String> result;
        try {
            result = httpAgent.httpPost(path, headers, paramValues, encode, readTimeoutMs);
        } finally {
            timer.observeDuration();
        }
        
        return result;
    }
    
    @Override
    public HttpRestResult<String> httpDelete(String path, Map<String, String> headers, Map<String, String> paramValues,
            String encode, long readTimeoutMs) throws Exception {
        Histogram.Timer timer = MetricsMonitor.getConfigRequestMonitor(DELETE, path, DEFAULT_CODE);
        HttpRestResult<String> result;
        try {
            result = httpAgent.httpDelete(path, headers, paramValues, encode, readTimeoutMs);
        } finally {
            timer.observeDuration();
        }
        
        return result;
    }
    
    @Override
    public String getName() {
        return httpAgent.getName();
    }
    
    @Override
    public String getNamespace() {
        return httpAgent.getNamespace();
    }
    
    @Override
    public String getTenant() {
        return httpAgent.getTenant();
    }
    
    @Override
    public String getEncode() {
        return httpAgent.getEncode();
    }
    
    @Override
    public void shutdown() throws NacosException {
        httpAgent.shutdown();
    }
}

