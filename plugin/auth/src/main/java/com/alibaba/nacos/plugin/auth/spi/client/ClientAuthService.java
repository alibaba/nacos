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

package com.alibaba.nacos.plugin.auth.spi.client;

import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.plugin.auth.api.RequestResource;

import java.util.List;
import java.util.Properties;

/**
 * Client AuthService.
 *
 * @author wuyfee
 */
public interface ClientAuthService extends Closeable {
    
    /**
     * login(request) to service and get response.
     *
     * @param properties login auth information.
     * @return boolean whether login success.
     */
    Boolean login(Properties properties);
    
    /**
     * set login serverList.
     *
     * @param serverList login server list;
     */
    void setServerList(List<String> serverList);
    
    /**
     * http request template.
     *
     * @param nacosRestTemplate nacos http request template.
     */
    void setNacosRestTemplate(NacosRestTemplate nacosRestTemplate);
    
    /**
     * get login identity context.
     *
     * @param resource resource for this request, some of plugin implementation will use this resource to generate their
     *                 identity context. If no need to use can ignore it.
     * @return LoginIdentityContext this plugin loginIdentityContext.
     */
    LoginIdentityContext getLoginIdentityContext(RequestResource resource);
    
}
