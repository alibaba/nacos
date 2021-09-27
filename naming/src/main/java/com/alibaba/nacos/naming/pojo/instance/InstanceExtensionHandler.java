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

package com.alibaba.nacos.naming.pojo.instance;

import com.alibaba.nacos.api.naming.pojo.Instance;

import javax.servlet.http.HttpServletRequest;

/**
 * Instance extension handler.
 *
 * <p>An extension handler for {@link Instance}, which is to handle some specified request for 1.x client.
 *
 * @author xiweng.yy
 */
public interface InstanceExtensionHandler {
    
    /**
     * Config extension info from http request.
     *
     * @param request http request
     */
    void configExtensionInfoFromRequest(HttpServletRequest request);
    
    /**
     * Do handle for instance.
     *
     * @param needHandleInstance instance needed to be handled.
     */
    void handleExtensionInfo(Instance needHandleInstance);
}
