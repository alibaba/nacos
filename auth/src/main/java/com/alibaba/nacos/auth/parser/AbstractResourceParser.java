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

package com.alibaba.nacos.auth.parser;

import com.alibaba.nacos.plugin.auth.api.Resource;

import java.util.Properties;

/**
 * Abstract Resource parser.
 *
 * @author xiweng.yy
 * @since 2.1.0
 */
public abstract class AbstractResourceParser<R> implements ResourceParser<R> {
    
    @Override
    public Resource parse(R request, String type) {
        String namespaceId = getNamespaceId(request);
        String group = getGroup(request);
        String name = getResourceName(request);
        Properties properties = getProperties(request);
        return new Resource(namespaceId, group, name, type, properties);
    }
    
    /**
     * Get namespaceId from request.
     *
     * @param request request
     * @return namespaceId
     */
    protected abstract String getNamespaceId(R request);
    
    /**
     * Get group name from request.
     *
     * @param request request
     * @return group name
     */
    protected abstract String getGroup(R request);
    
    /**
     * Get resource name from request.
     *
     * @param request request
     * @return resource name
     */
    protected abstract String getResourceName(R request);
    
    /**
     * Get custom properties from request.
     *
     * @param request request
     * @return custom properties
     */
    protected abstract Properties getProperties(R request);
}
