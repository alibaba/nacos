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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.naming.misc.HttpClientManager;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;
import static com.alibaba.nacos.naming.misc.Loggers.SRV_LOG;

/**
 * TCP health check processor for v2.x.
 *
 * <p>Current health check logic is same as v1.x. TODO refactor health check for v2.x.
 *
 * @author xiweng.yy
 */
@Component
public class HttpHealthCheckProcessor implements HealthCheckProcessorV2 {
    
    public static final String TYPE = HealthCheckType.HTTP.name();
    
    private static final NacosAsyncRestTemplate ASYNC_REST_TEMPLATE = HttpClientManager
            .getProcessorNacosAsyncRestTemplate();
    
    private final HealthCheckCommonV2 healthCheckCommon;
    
    private final SwitchDomain switchDomain;
    
    public HttpHealthCheckProcessor(HealthCheckCommonV2 healthCheckCommon, SwitchDomain switchDomain) {
        this.healthCheckCommon = healthCheckCommon;
        this.switchDomain = switchDomain;
    }
    
    @Override
    public void process(HealthCheckTaskV2 task, Service service, ClusterMetadata metadata) {
        HealthCheckInstancePublishInfo instance = (HealthCheckInstancePublishInfo) task.getClient()
                .getInstancePublishInfo(service);
        if (null == instance) {
            return;
        }
        try {
            // TODO handle marked(white list) logic like v1.x.
            if (!instance.tryStartCheck()) {
                SRV_LOG.warn("http check started before last one finished, service: {} : {} : {}:{}",
                        service.getGroupedServiceName(), instance.getCluster(), instance.getIp(), instance.getPort());
                healthCheckCommon
                        .reEvaluateCheckRt(task.getCheckRtNormalized() * 2, task, switchDomain.getHttpHealthParams());
                return;
            }
            
            Http healthChecker = (Http) metadata.getHealthChecker();
            int ckPort = metadata.isUseInstancePortForCheck() ? instance.getPort() : metadata.getHealthyCheckPort();
            URL host = new URL(HTTP_PREFIX + instance.getIp() + ":" + ckPort);
            URL target = new URL(host, healthChecker.getPath());
            Map<String, String> customHeaders = healthChecker.getCustomHeaders();
            Header header = Header.newInstance();
            header.addAll(customHeaders);
            
            ASYNC_REST_TEMPLATE.get(target.toString(), header, Query.EMPTY, String.class,
                    new HttpHealthCheckCallback(instance, task, service));
            MetricsMonitor.getHttpHealthCheckMonitor().incrementAndGet();
        } catch (Throwable e) {
            instance.setCheckRt(switchDomain.getHttpHealthParams().getMax());
            healthCheckCommon.checkFail(task, service, "http:error:" + e.getMessage());
            healthCheckCommon.reEvaluateCheckRt(switchDomain.getHttpHealthParams().getMax(), task,
                    switchDomain.getHttpHealthParams());
        }
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    private class HttpHealthCheckCallback implements Callback<String> {
        
        private final HealthCheckTaskV2 task;
        
        private final Service service;
        
        private final HealthCheckInstancePublishInfo instance;
        
        private long startTime = System.currentTimeMillis();
        
        public HttpHealthCheckCallback(HealthCheckInstancePublishInfo instance, HealthCheckTaskV2 task,
                Service service) {
            this.instance = instance;
            this.task = task;
            this.service = service;
        }
        
        @Override
        public void onReceive(RestResult<String> result) {
            instance.setCheckRt(System.currentTimeMillis() - startTime);
            int httpCode = result.getCode();
            if (HttpURLConnection.HTTP_OK == httpCode) {
                healthCheckCommon.checkOk(task, service, "http:" + httpCode);
                healthCheckCommon.reEvaluateCheckRt(System.currentTimeMillis() - startTime, task,
                        switchDomain.getHttpHealthParams());
            } else if (HttpURLConnection.HTTP_UNAVAILABLE == httpCode
                    || HttpURLConnection.HTTP_MOVED_TEMP == httpCode) {
                // server is busy, need verification later
                healthCheckCommon.checkFail(task, service, "http:" + httpCode);
                healthCheckCommon
                        .reEvaluateCheckRt(task.getCheckRtNormalized() * 2, task, switchDomain.getHttpHealthParams());
            } else {
                //probably means the state files has been removed by administrator
                healthCheckCommon.checkFailNow(task, service, "http:" + httpCode);
                healthCheckCommon.reEvaluateCheckRt(switchDomain.getHttpHealthParams().getMax(), task,
                        switchDomain.getHttpHealthParams());
            }
        }
        
        @Override
        public void onError(Throwable throwable) {
            Throwable cause = throwable;
            instance.setCheckRt(System.currentTimeMillis() - startTime);
            int maxStackDepth = 50;
            for (int deepth = 0; deepth < maxStackDepth && cause != null; deepth++) {
                if (HttpUtils.isTimeoutException(cause)) {
                    healthCheckCommon.checkFail(task, service, "http:" + cause.getMessage());
                    healthCheckCommon.reEvaluateCheckRt(task.getCheckRtNormalized() * 2, task,
                            switchDomain.getHttpHealthParams());
                    return;
                }
                cause = cause.getCause();
            }
            
            // connection error, probably not reachable
            if (throwable instanceof ConnectException) {
                healthCheckCommon.checkFailNow(task, service, "http:unable2connect:" + throwable.getMessage());
            } else {
                healthCheckCommon.checkFail(task, service, "http:error:" + throwable.getMessage());
            }
            healthCheckCommon.reEvaluateCheckRt(switchDomain.getHttpHealthParams().getMax(), task,
                    switchDomain.getHttpHealthParams());
        }
        
        @Override
        public void onCancel() {
        
        }
    }
}
