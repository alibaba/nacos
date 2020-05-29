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
import com.alibaba.nacos.common.lifecycle.AbstractLifeCycle;
import io.prometheus.client.Histogram;

import java.io.IOException;
import java.util.List;

/**
 * MetricsHttpAgent
 *
 * @author Nacos
 */
public class MetricsHttpAgent extends AbstractLifeCycle implements HttpAgent {

    private ServerHttpAgent serverHttpAgent;

    public MetricsHttpAgent(ServerHttpAgent serverHttpAgent) {
        this.serverHttpAgent = serverHttpAgent;
    }

    public void fetchServerIpList() throws NacosException {
        this.serverHttpAgent.fetchServerIpList();
    }

    @Override
    public HttpResult httpGet(String path, List<String> headers, List<String> paramValues, String encoding, long readTimeoutMs) throws IOException {
        Histogram.Timer timer = MetricsMonitor.getConfigRequestMonitor("GET", path, "NA");
        HttpResult result;
        try {
            result = this.serverHttpAgent.httpGet(path, headers, paramValues, encoding, readTimeoutMs);
        } catch (IOException e) {
            throw e;
        } finally {
            timer.observeDuration();
            timer.close();
        }

        return result;
    }

    @Override
    public HttpResult httpPost(String path, List<String> headers, List<String> paramValues, String encoding, long readTimeoutMs) throws IOException {
        Histogram.Timer timer = MetricsMonitor.getConfigRequestMonitor("POST", path, "NA");
        HttpResult result;
        try {
            result = this.serverHttpAgent.httpPost(path, headers, paramValues, encoding, readTimeoutMs);
        } catch (IOException e) {
            throw e;
        } finally {
            timer.observeDuration();
            timer.close();
        }

        return result;
    }

    @Override
    public HttpResult httpDelete(String path, List<String> headers, List<String> paramValues, String encoding, long readTimeoutMs) throws IOException {
        Histogram.Timer timer = MetricsMonitor.getConfigRequestMonitor("DELETE", path, "NA");
        HttpResult result;
        try {
            result = this.serverHttpAgent.httpDelete(path, headers, paramValues, encoding, readTimeoutMs);
        } catch (IOException e) {

            throw e;
        } finally {
            timer.observeDuration();
            timer.close();
        }

        return result;
    }

    @Override
    public String getName() {
        return this.serverHttpAgent.getName();
    }

    @Override
    public String getNamespace() {
        return this.serverHttpAgent.getNamespace();
    }

    @Override
    public String getTenant() {
        return this.serverHttpAgent.getTenant();
    }

    @Override
    public String getEncode() {
        return this.serverHttpAgent.getEncode();
    }

    @Override
    public void doStart() throws Exception {
        this.serverHttpAgent.doStart();
    }

    @Override
    public void doStop() throws Exception {
        this.serverHttpAgent.doStop();
    }
}

