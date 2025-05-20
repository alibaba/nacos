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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

public class HttpClientManagerTest {
    
    @BeforeEach
    void setUp() throws IllegalAccessException, NoSuchFieldException {
        Field instanceField = HttpClientManager.class.getDeclaredField("httpClientManager");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        HttpClientManager.getInstance().shutdown();
    }
    
    @Test
    public void testGetNacosRestTemplate() {
        HttpClientManager httpClientManager = HttpClientManager.getInstance();
        NacosRestTemplate template = httpClientManager.getNacosRestTemplate();
        assert template != null : "NacosRestTemplate should not be null.";
    }
}