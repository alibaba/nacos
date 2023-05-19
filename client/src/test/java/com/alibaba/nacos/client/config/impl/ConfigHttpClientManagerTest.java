/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.junit.Assert;
import org.junit.Test;

public class ConfigHttpClientManagerTest {
    
    @Test
    public void test() {
        final ConfigHttpClientManager instance1 = ConfigHttpClientManager.getInstance();
        final ConfigHttpClientManager instance2 = ConfigHttpClientManager.getInstance();
        
        Assert.assertEquals(instance1, instance2);
    
        final NacosRestTemplate nacosRestTemplate = instance1.getNacosRestTemplate();
        Assert.assertNotNull(nacosRestTemplate);
    
        final int time1 = instance1.getConnectTimeoutOrDefault(10);
        Assert.assertEquals(1000, time1);
        final int time2 = instance1.getConnectTimeoutOrDefault(2000);
        Assert.assertEquals(2000, time2);
    
        try {
            instance1.shutdown();
        } catch (NacosException e) {
            Assert.fail();
        }
    }

}
