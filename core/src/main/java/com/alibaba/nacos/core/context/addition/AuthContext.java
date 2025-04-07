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

package com.alibaba.nacos.core.context.addition;

import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Resource;

/**
 * Nacos auth context, store and transport some auth plugin information to handler or trace log.
 *
 * @author xiweng.yy
 */
public class AuthContext {
    
    private IdentityContext identityContext;
    
    private Resource resource;
    
    /**
     * Auth result, default is {@code true} or {@code false}.
     *
     * <p>TODO with more auth result by auth plugin.
     */
    private Object authResult;
    
    public IdentityContext getIdentityContext() {
        return identityContext;
    }
    
    public void setIdentityContext(IdentityContext identityContext) {
        this.identityContext = identityContext;
    }
    
    public Resource getResource() {
        return resource;
    }
    
    public void setResource(Resource resource) {
        this.resource = resource;
    }
    
    public Object getAuthResult() {
        return authResult;
    }
    
    public void setAuthResult(Object authResult) {
        this.authResult = authResult;
    }
}
