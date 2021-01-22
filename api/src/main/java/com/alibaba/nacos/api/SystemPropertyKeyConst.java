/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api;

/**
 * Support for reading the value of the specified variable from the -D parameter.
 *
 * <p>Properties that are preferred to which in {@link PropertyKeyConst}
 *
 * @author pbting
 */
public interface SystemPropertyKeyConst {
    
    String NAMING_SERVER_PORT = "nacos.naming.exposed.port";
    
    String NAMING_WEB_CONTEXT = "nacos.naming.web.context";
    
    /**
     * In the cloud (Alibaba Cloud or other cloud vendors) environment, whether to enable namespace resolution in the
     * cloud environment.
     * <p>
     * The default is on.
     * </p>
     */
    String IS_USE_CLOUD_NAMESPACE_PARSING = "nacos.use.cloud.namespace.parsing";
    
    /**
     * In the cloud environment, if the process level requires a globally uniform namespace, it can be specified with
     * the -D parameter.
     */
    String ANS_NAMESPACE = "ans.namespace";
    
    /**
     * It is also supported by the -D parameter.
     */
    String IS_USE_ENDPOINT_PARSING_RULE = "nacos.use.endpoint.parsing.rule";
}
