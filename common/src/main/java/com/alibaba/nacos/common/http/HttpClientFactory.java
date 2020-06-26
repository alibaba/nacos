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

package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;

/**
 * http Client Factory.
 *
 * @author mai.jh
 */
public interface HttpClientFactory {
    
    /**
     * create new nacost rest.
     *
     * @return NacosRestTemplate
     */
    NacosRestTemplate createNacosRestTemplate();
    
    /**
     * create new nacos async rest.
     *
     * @return NacosAsyncRestTemplate
     */
    NacosAsyncRestTemplate createNacosAsyncRestTemplate();
    
}
