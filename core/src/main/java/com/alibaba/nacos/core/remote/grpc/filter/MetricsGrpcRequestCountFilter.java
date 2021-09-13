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
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.core.monitor.MetricsMonitor;
import com.alibaba.nacos.core.remote.AbstractRequestFilter;
import com.alibaba.nacos.manager.CoreMetricsConstant;
import com.alibaba.nacos.manager.MetricsManager;
import org.springframework.stereotype.Component;

/**
 * Grpc request count filter.
 *
 * @author holdonbei
 */
@Component
public class MetricsGrpcRequestCountFilter extends AbstractRequestFilter {
    
    /**
     * metrics all grpc request when grpc request happen.
     */
    @Override
    protected Response filter(Request request, RequestMeta meta, Class handlerClazz) throws NacosException {
        MetricsManager.counter(CoreMetricsConstant.N_NACOS_GRPC_REQUEST_COUNT,
                CoreMetricsConstant.TK_MODULE, CoreMetricsConstant.TV_CORE,
                CoreMetricsConstant.TK_NAME, request.getClass().getSimpleName())
                .increment();
        return null;
    }
}
