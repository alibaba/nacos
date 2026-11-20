/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth.parser.grpc;

import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.remote.request.LockOperationRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Lock Grpc resource parser.
 *
 * <p>Lock resources are identified by {@code lockType -> key} and are global to the Nacos
 * cluster. The current lock resource model does not contain namespace or group dimensions, so
 * the parser returns empty namespace and group, and uses the lock key as the resource name.
 */
public class LockGrpcResourceParser extends AbstractGrpcResourceParser {
    
    @Override
    protected String getNamespaceId(Request request) {
        return StringUtils.EMPTY;
    }
    
    @Override
    protected String getGroup(Request request) {
        return StringUtils.EMPTY;
    }
    
    @Override
    protected String getResourceName(Request request) {
        if (!(request instanceof LockOperationRequest)) {
            return StringUtils.EMPTY;
        }
        LockInstance lockInstance = ((LockOperationRequest) request).getLockInstance();
        if (lockInstance == null || StringUtils.isBlank(lockInstance.getKey())) {
            return StringUtils.EMPTY;
        }
        return lockInstance.getKey();
    }
}
