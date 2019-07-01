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
package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.api.naming.pojo.AbstractHealthChecker;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;
import io.netty.channel.ConnectTimeoutException;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static com.alibaba.nacos.naming.misc.Loggers.SRV_LOG;

/**
 * HTTP health check processor
 *
 * @author xuanyin.zy
 */
@Component
public class HttpHealthCheckProcessor implements HealthCheckProcessor {

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private HealthCheckCommon healthCheckCommon;

    private static AsyncHttpClient asyncHttpClient;

    private static final int CONNECT_TIMEOUT_MS = 500;

    static {
        try {
            AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder();

            builder.setMaximumConnectionsTotal(-1);
            builder.setMaximumConnectionsPerHost(-1);
            builder.setAllowPoolingConnection(false);
            builder.setFollowRedirects(false);
            builder.setIdleConnectionTimeoutInMs(CONNECT_TIMEOUT_MS);
            builder.setConnectionTimeoutInMs(CONNECT_TIMEOUT_MS);
            builder.setCompressionEnabled(false);
            builder.setIOThreadMultiplier(1);
            builder.setMaxRequestRetry(0);
            builder.setUserAgent("VIPServer");
            asyncHttpClient = new AsyncHttpClient(builder.build());
        } catch (Throwable e) {
            SRV_LOG.error("[HEALTH-CHECK] Error while constructing HTTP asynchronous client", e);
        }
    }

    @Override
    public String getType() {
        return "HTTP";
    }

    @Override
    public void process(HealthCheckTask task) {
        List<Instance> ips = task.getCluster().allIPs(false);
        if (CollectionUtils.isEmpty(ips)) {
            return;
        }

        if (!switchDomain.isHealthCheckEnabled()) {
            return;
        }

        Cluster cluster = task.getCluster();

        for (Instance ip : ips) {
            try {

                if (ip.isMarked()) {
                    if (SRV_LOG.isDebugEnabled()) {
                        SRV_LOG.debug("http check, ip is marked as to skip health check, ip: {}" + ip.getIp());
                    }
                    continue;
                }

                if (!ip.markChecking()) {
                    SRV_LOG.warn("http check started before last one finished, service: {}:{}:{}",
                        task.getCluster().getService().getName(), task.getCluster().getName(), ip.getIp());

                    healthCheckCommon.reEvaluateCheckRT(task.getCheckRTNormalized() * 2, task, switchDomain.getHttpHealthParams());
                    continue;
                }

                AbstractHealthChecker.Http healthChecker = (AbstractHealthChecker.Http) cluster.getHealthChecker();

                int ckPort = cluster.isUseIPPort4Check() ? ip.getPort() : cluster.getDefCkport();
                URL host = new URL("http://" + ip.getIp() + ":" + ckPort);
                URL target = new URL(host, healthChecker.getPath());

                AsyncHttpClient.BoundRequestBuilder builder = asyncHttpClient.prepareGet(target.toString());
                Map<String, String> customHeaders = healthChecker.getCustomHeaders();
                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    if ("Host".equals(entry.getKey())) {
                        builder.setVirtualHost(entry.getValue());
                        continue;
                    }

                    builder.setHeader(entry.getKey(), entry.getValue());
                }

                builder.execute(new HttpHealthCheckCallback(ip, task));
                MetricsMonitor.getHttpHealthCheckMonitor().incrementAndGet();
            } catch (Throwable e) {
                ip.setCheckRT(switchDomain.getHttpHealthParams().getMax());
                healthCheckCommon.checkFail(ip, task, "http:error:" + e.getMessage());
                healthCheckCommon.reEvaluateCheckRT(switchDomain.getHttpHealthParams().getMax(), task, switchDomain.getHttpHealthParams());
            }
        }
    }

    private class HttpHealthCheckCallback extends AsyncCompletionHandler<Integer> {
        private Instance ip;
        private HealthCheckTask task;

        private long startTime = System.currentTimeMillis();

        public HttpHealthCheckCallback(Instance ip, HealthCheckTask task) {
            this.ip = ip;
            this.task = task;
        }

        @Override
        public Integer onCompleted(Response response) throws Exception {
            ip.setCheckRT(System.currentTimeMillis() - startTime);

            int httpCode = response.getStatusCode();
            if (HttpURLConnection.HTTP_OK == httpCode) {
                healthCheckCommon.checkOK(ip, task, "http:" + httpCode);
                healthCheckCommon.reEvaluateCheckRT(System.currentTimeMillis() - startTime, task, switchDomain.getHttpHealthParams());
            } else if (HttpURLConnection.HTTP_UNAVAILABLE == httpCode || HttpURLConnection.HTTP_MOVED_TEMP == httpCode) {
                // server is busy, need verification later
                healthCheckCommon.checkFail(ip, task, "http:" + httpCode);
                healthCheckCommon.reEvaluateCheckRT(task.getCheckRTNormalized() * 2, task, switchDomain.getHttpHealthParams());
            } else {
                //probably means the state files has been removed by administrator
                healthCheckCommon.checkFailNow(ip, task, "http:" + httpCode);
                healthCheckCommon.reEvaluateCheckRT(switchDomain.getHttpHealthParams().getMax(), task, switchDomain.getHttpHealthParams());
            }

            return httpCode;
        }

        @Override
        public void onThrowable(Throwable t) {
            ip.setCheckRT(System.currentTimeMillis() - startTime);

            Throwable cause = t;
            int maxStackDepth = 50;
            for (int deepth = 0; deepth < maxStackDepth && cause != null; deepth++) {
                if (cause instanceof SocketTimeoutException
                        || cause instanceof ConnectTimeoutException
                        || cause instanceof org.jboss.netty.channel.ConnectTimeoutException
                        || cause instanceof TimeoutException
                        || cause.getCause() instanceof TimeoutException) {

                    healthCheckCommon.checkFail(ip, task, "http:timeout:" + cause.getMessage());
                    healthCheckCommon.reEvaluateCheckRT(task.getCheckRTNormalized() * 2, task, switchDomain.getHttpHealthParams());

                    return;
                }

                cause = cause.getCause();
            }

            // connection error, probably not reachable
            if (t instanceof ConnectException) {
                healthCheckCommon.checkFailNow(ip, task, "http:unable2connect:" + t.getMessage());
                healthCheckCommon.reEvaluateCheckRT(switchDomain.getHttpHealthParams().getMax(), task, switchDomain.getHttpHealthParams());
            } else {
                healthCheckCommon.checkFail(ip, task, "http:error:" + t.getMessage());
                healthCheckCommon.reEvaluateCheckRT(switchDomain.getHttpHealthParams().getMax(), task, switchDomain.getHttpHealthParams());
            }
        }
    }
}
