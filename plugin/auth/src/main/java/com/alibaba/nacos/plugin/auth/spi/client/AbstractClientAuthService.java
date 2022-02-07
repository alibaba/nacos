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

import com.alibaba.nacos.common.http.client.NacosRestTemplate;

import java.util.List;

/**
 * Client auth services SPI.
 *
 * @author Nacos
 */
public abstract class AbstractClientAuthService implements ClientAuthService {
    
    protected List<String> serverList;
    
    protected NacosRestTemplate nacosRestTemplate;
    
    @Override
    public void setServerList(List<String> serverList) {
        this.serverList = serverList;
    }
    
    @Override
    public void setNacosRestTemplate(NacosRestTemplate nacosRestTemplate) {
        this.nacosRestTemplate = nacosRestTemplate;
    }
}
