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

package com.alibaba.nacos.plugin.auth.api;

import java.io.Serializable;

/**
 * Permission to auth.
 *
 * @author nkorange
 * @author mai.jh
 * @author xiweng.yy
 * @since 1.2.0
 */
public class Permission implements Serializable {
    
    private static final long serialVersionUID = -3583076254743606551L;
    
    /**
     * An unique key of resource.
     */
    private Resource resource;
    
    /**
     * Action on resource, refer to class ActionTypes.
     */
    private String action;
    
    /**
     * Optional request target that triggered this authorization check.
     *
     * <p>For HTTP it is {@code ${method} ${uri}}, such as
     * {@code GET /v3/console/cs/config/list}. For gRPC it is the request class simple name,
     * such as {@code ConfigQueryRequest}. It is {@code null} when the check is not triggered
     * by a protocol request, such as an internal visibility check.</p>
     *
     * @since 3.3.1
     */
    private String requestTarget;
    
    public Permission() {
    }
    
    public Permission(Resource resource, String action) {
        this(resource, action, null);
    }
    
    /**
     * Create a permission with the request target that triggered the authorization check.
     *
     * @param resource      resource to authorize
     * @param action        action on the resource
     * @param requestTarget request target, or {@code null} when unknown
     */
    public Permission(Resource resource, String action, String requestTarget) {
        this.resource = resource;
        this.action = action;
        this.requestTarget = requestTarget;
    }
    
    public Resource getResource() {
        return resource;
    }
    
    public void setResource(Resource resource) {
        this.resource = resource;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    /**
     * Get the request target that triggered this authorization check.
     *
     * @return request target, or {@code null} when the check is not triggered by a protocol
     *     request
     */
    public String getRequestTarget() {
        return requestTarget;
    }
    
    /**
     * Set the request target that triggered this authorization check.
     *
     * @param requestTarget request target, or {@code null} when unknown
     */
    public void setRequestTarget(String requestTarget) {
        this.requestTarget = requestTarget;
    }
    
    @Override
    public String toString() {
        return "Permission{" + "resource='" + resource + '\'' + ", action='" + action + '\''
            + ", requestTarget='" + requestTarget + '\'' + '}';
    }
}
