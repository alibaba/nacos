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
import com.alibaba.nacos.client.config.impl.HttpSimpleClient.HttpResult;
import com.alibaba.nacos.client.monitor.MetricsMonitor;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MetricsHttpAgent
 *
 * @author Nacos
 */
public class MetricsHttpAgent implements HttpAgent {
    private HttpAgent httpAgent;

    public MetricsHttpAgent(HttpAgent httpAgent) {
        this.httpAgent = httpAgent;
    }

    @Override
    public void start() throws NacosException {
        httpAgent.start();
    }

    @Override
    public HttpResult httpGet(String path, List<String> headers, List<String> paramValues, String encoding, long readTimeoutMs) throws IOException {
        long start = System.currentTimeMillis();
        long end = 0;
        HttpResult result = null;
        try {
            result = httpAgent.httpGet(path, headers, paramValues, encoding, readTimeoutMs);
        } catch (IOException e) {
            end = System.currentTimeMillis();
            MetricsMonitor.getConfigRequestMonitor("GET", path, "NA").record(end - start, TimeUnit.MILLISECONDS);
            throw e;
        }

        end = System.currentTimeMillis();
        MetricsMonitor.getConfigRequestMonitor("GET", path, String.valueOf(result.code)).record(end - start, TimeUnit.MILLISECONDS);

        return result;
    }

    @Override
    public HttpResult httpPost(String path, List<String> headers, List<String> paramValues, String encoding, long readTimeoutMs) throws IOException {
        long start = System.currentTimeMillis();
        long end = 0;
        HttpResult result = null;
        try {
            result = httpAgent.httpPost(path, headers, paramValues, encoding, readTimeoutMs);
        } catch (IOException e) {
            end = System.currentTimeMillis();
            MetricsMonitor.getConfigRequestMonitor("POST", path, "NA").record(end - start, TimeUnit.MILLISECONDS);
            throw e;
        }

        end = System.currentTimeMillis();
        MetricsMonitor.getConfigRequestMonitor("POST", path, String.valueOf(result.code)).record(end - start, TimeUnit.MILLISECONDS);

        return result;
    }

    @Override
    public HttpResult httpDelete(String path, List<String> headers, List<String> paramValues, String encoding, long readTimeoutMs) throws IOException {
        long start = System.currentTimeMillis();
        long end = 0;
        HttpResult result = null;
        try {
            result = httpAgent.httpDelete(path, headers, paramValues, encoding, readTimeoutMs);
        } catch (IOException e) {
            end = System.currentTimeMillis();
            MetricsMonitor.getConfigRequestMonitor("DELETE", path, "NA").record(end - start, TimeUnit.MILLISECONDS);
            throw e;
        }

        end = System.currentTimeMillis();
        MetricsMonitor.getConfigRequestMonitor("DELETE", path, String.valueOf(result.code)).record(end - start, TimeUnit.MILLISECONDS);

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
}
