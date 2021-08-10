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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import com.alibaba.nacos.core.remote.control.TpsControl;
import org.springframework.stereotype.Component;

/**
 * push response  to clients.
 *
 * @author liuzunfei
 * @version $Id: PushService.java, v 0.1 2021年07月17日 1:12 PM liuzunfei Exp $
 */
@Component
public class HealthCheckRequestHandler extends RequestHandler<HealthCheckRequest, HealthCheckResponse> {
    
    @Override
    @TpsControl(pointName = "HealthCheck")
    public HealthCheckResponse handle(HealthCheckRequest request, RequestMeta meta) {
        return new HealthCheckResponse();
    }
}
