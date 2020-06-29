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

package com.alibaba.nacos.api.annotation;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import static com.alibaba.nacos.api.annotation.NacosProperties.ACCESS_KEY_PLACEHOLDER;
import static com.alibaba.nacos.api.annotation.NacosProperties.CLUSTER_NAME_PLACEHOLDER;
import static com.alibaba.nacos.api.annotation.NacosProperties.CONTEXT_PATH_PLACEHOLDER;
import static com.alibaba.nacos.api.annotation.NacosProperties.ENCODE_PLACEHOLDER;
import static com.alibaba.nacos.api.annotation.NacosProperties.ENDPOINT_PLACEHOLDER;
import static com.alibaba.nacos.api.annotation.NacosProperties.NAMESPACE_PLACEHOLDER;
import static com.alibaba.nacos.api.annotation.NacosProperties.SECRET_KEY_PLACEHOLDER;
import static com.alibaba.nacos.api.annotation.NacosProperties.SERVER_ADDR_PLACEHOLDER;

public class NacosPropertiesTest {
    
    @Test
    public void testPlaceholders() {
        Assert.assertEquals("${nacos.endpoint:}", ENDPOINT_PLACEHOLDER);
        Assert.assertEquals("${nacos.namespace:}", NAMESPACE_PLACEHOLDER);
        Assert.assertEquals("${nacos.access-key:}", ACCESS_KEY_PLACEHOLDER);
        Assert.assertEquals("${nacos.secret-key:}", SECRET_KEY_PLACEHOLDER);
        Assert.assertEquals("${nacos.server-addr:}", SERVER_ADDR_PLACEHOLDER);
        Assert.assertEquals("${nacos.context-path:}", CONTEXT_PATH_PLACEHOLDER);
        Assert.assertEquals("${nacos.cluster-name:}", CLUSTER_NAME_PLACEHOLDER);
        Assert.assertEquals("${nacos.encode:UTF-8}", ENCODE_PLACEHOLDER);
    }
    
    @Test
    public void testResolvePlaceholders() {
        testResolvePlaceholder(ENDPOINT_PLACEHOLDER, "nacos.endpoint", "test-value", "test-value");
        testResolvePlaceholder(ENDPOINT_PLACEHOLDER, "", "test-value", "");
        
        testResolvePlaceholder(NAMESPACE_PLACEHOLDER, "nacos.namespace", "test-value", "test-value");
        testResolvePlaceholder(NAMESPACE_PLACEHOLDER, "", "test-value", "");
        
        testResolvePlaceholder(ACCESS_KEY_PLACEHOLDER, "nacos.access-key", "test-value", "test-value");
        testResolvePlaceholder(ACCESS_KEY_PLACEHOLDER, "", "test-value", "");
        
        testResolvePlaceholder(SECRET_KEY_PLACEHOLDER, "nacos.secret-key", "test-value", "test-value");
        testResolvePlaceholder(SECRET_KEY_PLACEHOLDER, "", "test-value", "");
        
        testResolvePlaceholder(SERVER_ADDR_PLACEHOLDER, "nacos.server-addr", "test-value", "test-value");
        testResolvePlaceholder(SERVER_ADDR_PLACEHOLDER, "", "test-value", "");
        
        testResolvePlaceholder(CONTEXT_PATH_PLACEHOLDER, "nacos.context-path", "test-value", "test-value");
        testResolvePlaceholder(CONTEXT_PATH_PLACEHOLDER, "", "test-value", "");
        
        testResolvePlaceholder(CLUSTER_NAME_PLACEHOLDER, "nacos.cluster-name", "test-value", "test-value");
        testResolvePlaceholder(CLUSTER_NAME_PLACEHOLDER, "", "test-value", "");
        
        testResolvePlaceholder(ENCODE_PLACEHOLDER, "nacos.encode", "test-value", "test-value");
        testResolvePlaceholder(ENCODE_PLACEHOLDER, "", "test-value", "UTF-8");
    }
    
    private void testResolvePlaceholder(String placeholder, String propertyName, String propertyValue,
            String expectValue) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(propertyName, propertyValue);
        String resolvedValue = environment.resolvePlaceholders(placeholder);
        Assert.assertEquals(expectValue, resolvedValue);
    }
    
    @Test
    public void testSort() {
    
    }
}
