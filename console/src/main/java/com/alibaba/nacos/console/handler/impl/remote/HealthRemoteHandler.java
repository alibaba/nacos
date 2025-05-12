/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.console.handler.HealthHandler;
import org.springframework.stereotype.Service;

/**
 * Remote Implementation of HealthHandler that performs health check operations.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class HealthRemoteHandler implements HealthHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public HealthRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public Result<String> checkReadiness() throws NacosException {
        Boolean result = clientHolder.getNamingMaintainerService().readiness();
        return result ? Result.success("ok") : Result.failure("Nacos server readiness failed.");
    }
}

