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

package com.alibaba.nacos.core.remote.grpc.filter;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.ClientDetectionRequest;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.request.PushAckRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerCheckRequest;
import com.alibaba.nacos.api.remote.request.ServerReloadRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.core.monitor.MetricsMonitor;
import com.alibaba.nacos.core.remote.AbstractRequestFilter;
import org.springframework.stereotype.Component;

/**
 * Grpc request count filter.
 *
 * @author holdonbei
 */
@Component
public class MetricsGrpcRequestCountFilter extends AbstractRequestFilter {
    
    @Override
    protected Response filter(Request request, RequestMeta meta, Class handlerClazz) throws NacosException {
        if (request instanceof HealthCheckRequest) {
            MetricsMonitor.getHealthCheckRequestGrpcCount().increment();
        } else if (request instanceof ServerCheckRequest) {
            MetricsMonitor.getServerCheckRequestGrpcCount().increment();
        } else if (request instanceof ServerReloadRequest) {
            MetricsMonitor.getServerReloadRequestGrpcCount().increment();
        } else if (request instanceof PushAckRequest) {
            MetricsMonitor.getPushAckRequestGrpcCount().increment();
        } else if (request instanceof ClientDetectionRequest) {
            MetricsMonitor.getClientDetectionRequestGrpcCount().increment();
        }
        return null;
    }
    
}
