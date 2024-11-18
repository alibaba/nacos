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

package com.alibaba.nacos.naming.push.v2;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.constants.PushConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PushConfigTest {
    
    private PushConfig pushConfig;
    
    private MockEnvironment mockEnvironment;
    
    private long pushTaskDelay = PushConstants.DEFAULT_PUSH_TASK_DELAY * 2;
    
    private long pushTaskTimeout = PushConstants.DEFAULT_PUSH_TASK_TIMEOUT * 2;
    
    private long pushTaskRetryDelay = PushConstants.DEFAULT_PUSH_TASK_RETRY_DELAY * 2;
    
    @BeforeEach
    void setUp() throws Exception {
        mockEnvironment = new MockEnvironment();
        EnvUtil.setEnvironment(mockEnvironment);
        pushConfig = PushConfig.getInstance();
    }
    
    @Test
    void testUpgradeConfig() throws InterruptedException {
        mockEnvironment.setProperty(PushConstants.PUSH_TASK_DELAY, String.valueOf(pushTaskDelay));
        mockEnvironment.setProperty(PushConstants.PUSH_TASK_TIMEOUT, String.valueOf(pushTaskTimeout));
        mockEnvironment.setProperty(PushConstants.PUSH_TASK_RETRY_DELAY, String.valueOf(pushTaskRetryDelay));
        NotifyCenter.publishEvent(ServerConfigChangeEvent.newEvent());
        TimeUnit.SECONDS.sleep(1);
        assertEquals(pushTaskDelay, pushConfig.getPushTaskDelay());
        assertEquals(pushTaskTimeout, pushConfig.getPushTaskTimeout());
        assertEquals(pushTaskRetryDelay, pushConfig.getPushTaskRetryDelay());
    }
    
    @Test
    void testInitConfigFormEnv() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        mockEnvironment.setProperty(PushConstants.PUSH_TASK_DELAY, String.valueOf(pushTaskDelay));
        mockEnvironment.setProperty(PushConstants.PUSH_TASK_TIMEOUT, String.valueOf(pushTaskTimeout));
        mockEnvironment.setProperty(PushConstants.PUSH_TASK_RETRY_DELAY, String.valueOf(pushTaskRetryDelay));
        Constructor<PushConfig> declaredConstructor = PushConfig.class.getDeclaredConstructor();
        declaredConstructor.setAccessible(true);
        PushConfig pushConfig = declaredConstructor.newInstance();
        assertEquals(pushTaskDelay, pushConfig.getPushTaskDelay());
        assertEquals(pushTaskTimeout, pushConfig.getPushTaskTimeout());
        assertEquals(pushTaskRetryDelay, pushConfig.getPushTaskRetryDelay());
        
    }
}
