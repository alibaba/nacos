/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.maintainer.client.remote;

import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientManagerTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientManagerTest.class);
    
    @Test
    public void testGetNacosRestTemplate() {
        try {
            HttpClientManager httpClientManager = HttpClientManager.getInstance();
            NacosRestTemplate template = httpClientManager.getNacosRestTemplate();
            assert template != null : "NacosRestTemplate should not be null.";
            LOGGER.info("Successfully obtained NacosRestTemplate instance.");
        } catch (Exception e) {
            LOGGER.error("Failed to get NacosRestTemplate.", e);
        }
    }
    
    @Test
    public void testGetNacosAsyncRestTemplate() {
        try {
            HttpClientManager httpClientManager = HttpClientManager.getInstance();
            NacosAsyncRestTemplate asyncTemplate = httpClientManager.getNacosAsyncRestTemplate();
            assert asyncTemplate != null : "NacosAsyncRestTemplate should not be null.";
            LOGGER.info("Successfully obtained NacosAsyncRestTemplate instance.");
        } catch (Exception e) {
            LOGGER.error("Failed to get NacosAsyncRestTemplate.", e);
        }
    }
    
    @Test
    public void testShutdown() {
        try {
            HttpClientManager.shutdown(HttpClientManager.class.getName());
            LOGGER.info("Successfully shutdown HttpClientManager.");
        } catch (Exception e) {
            LOGGER.error("Failed to shutdown HttpClientManager.", e);
        }
    }

}