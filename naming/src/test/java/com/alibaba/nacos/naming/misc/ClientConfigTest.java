/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class ClientConfigTest {
    
    private static final long EXPIRED_TIME = 10000L;
    
    private ClientConfig clientConfig;
    
    private MockEnvironment mockEnvironment;
    
    @Before
    public void setUp() throws Exception {
        mockEnvironment = new MockEnvironment();
        EnvUtil.setEnvironment(mockEnvironment);
        clientConfig = ClientConfig.getInstance();
    }
    
    @Test
    public void testUpgradeConfig() throws InterruptedException {
        mockEnvironment.setProperty(ClientConstants.CLIENT_EXPIRED_TIME_CONFIG_KEY, String.valueOf(EXPIRED_TIME));
        NotifyCenter.publishEvent(ServerConfigChangeEvent.newEvent());
        TimeUnit.SECONDS.sleep(1);
        assertEquals(EXPIRED_TIME, clientConfig.getClientExpiredTime());
    }
    
    @Test
    public void testInitConfigFormEnv()
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        mockEnvironment.setProperty(ClientConstants.CLIENT_EXPIRED_TIME_CONFIG_KEY, String.valueOf(EXPIRED_TIME));
        Constructor<ClientConfig> declaredConstructor = ClientConfig.class.getDeclaredConstructor();
        declaredConstructor.setAccessible(true);
        ClientConfig clientConfig = declaredConstructor.newInstance();
        Assert.assertEquals(clientConfig.getClientExpiredTime(), EXPIRED_TIME);
    }
}
