/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.exception.AccessException;

/**
 * Protocol auth service.
 *
 * @author xiweng.yy
 */
public interface ProtocolAuthService<R> {
    
    /**
     * Init protocol auth service.
     */
    void initialize();
    
    /**
     * Parse resource from protocol request and secured annotation.
     *
     * @param request protocol request
     * @param secured api secured annotation
     * @return resource
     */
    Resource parseResource(R request, Secured secured);
    
    /**
     * Parse identity context from protocol request.
     *
     * @param request protocol request
     * @return identity context
     */
    IdentityContext parseIdentity(R request);
    
    /**
     * Validate identity whether is legal.
     *
     * @param identityContext identity context
     * @return {@code true} if legal, otherwise {@code false}
     * @throws AccessException exception during validating
     */
    boolean validateIdentity(IdentityContext identityContext) throws AccessException;
    
    /**
     * Validate identity whether had permission for the resource and action.
     *
     * @param identityContext identity context
     * @param permission      permssion include resource and action
     * @return {@code true} if legal, otherwise {@code false}
     * @throws AccessException exception during validating
     */
    boolean validateAuthority(IdentityContext identityContext, Permission permission) throws AccessException;
}
