/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.auth.context;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.auth.common.AuthConfigs;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Identity context builder for Grpc.
 *
 * @author Nacos
 */
public class GrpcIdentityContextBuilder implements IdentityContextBuilder<Request> {
    
    AuthConfigs authConfigs;
    
    public GrpcIdentityContextBuilder() {
        authConfigs = new AuthConfigs();
    }
    
    public GrpcIdentityContextBuilder(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
    }
    /**
     * get identity context from grpc.
     * @param request grpc request
     * @return IdentityContext request context
     */
    
    @Override
    public IdentityContext build(Request request) {
        Set<String> keySet = new HashSet<String>(Arrays.asList(authConfigs.getAuthorityKey()));
        IdentityContext identityContext = new IdentityContext();
        Map<String, String> map = request.getHeaders();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (keySet.contains(entry.getKey())) {
                identityContext.setParameter(entry.getKey(), entry.getValue());
            }
        }
        return identityContext;
    }
}
