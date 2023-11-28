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

package com.alibaba.nacos.client.config.filter.impl;

import com.alibaba.nacos.api.config.filter.IConfigFilterChain;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * ConfigCryptoFilterTest.
 *
 * @author lixiaoshuang
 */
@RunWith(MockitoJUnitRunner.class)
public class ConfigEncryptionFilterTest {
    
    private ConfigEncryptionFilter configEncryptionFilter;
    
    @Mock
    private ConfigRequest configRequest;
    
    @Mock
    private ConfigResponse configResponse;
    
    @Mock
    private IConfigFilterChain iConfigFilterChain;
    
    @Before
    public void setUp() throws Exception {
        configEncryptionFilter = new ConfigEncryptionFilter();
        
        Mockito.when(configRequest.getDataId()).thenReturn("cipher-aes-test");
        Mockito.when(configRequest.getContent()).thenReturn("nacos");
        
        Mockito.when(configResponse.getDataId()).thenReturn("test-dataid");
        Mockito.when(configResponse.getContent()).thenReturn("nacos");
        Mockito.when(configResponse.getEncryptedDataKey()).thenReturn("1234567890");
    }
    
    @Test
    public void testDoFilter() throws NacosException {
        configEncryptionFilter.doFilter(configRequest, null, iConfigFilterChain);
        
        Mockito.verify(configRequest, Mockito.atLeast(1)).getDataId();
        Mockito.verify(configRequest, Mockito.atLeast(1)).getContent();
        
        configEncryptionFilter.doFilter(null, configResponse, iConfigFilterChain);
        
        Mockito.verify(configResponse, Mockito.atLeast(1)).getDataId();
        Mockito.verify(configResponse, Mockito.atLeast(1)).getContent();
        Mockito.verify(configResponse, Mockito.atLeast(1)).getEncryptedDataKey();
    }
    
    @Test
    public void testGetOrder() {
        int order = configEncryptionFilter.getOrder();
        Assert.assertEquals(order, 0);
    }
}