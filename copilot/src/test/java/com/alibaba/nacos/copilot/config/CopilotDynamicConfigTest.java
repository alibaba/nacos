/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.copilot.config;

import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.NotifyCenter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Test for CopilotDynamicConfig.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class CopilotDynamicConfigTest {
    
    @Mock
    private CopilotAgentManager agentManager;
    
    private CopilotDynamicConfig dynamicConfig;
    
    @AfterEach
    void tearDown() {
        if (dynamicConfig != null) {
            dynamicConfig.destroy();
        }
    }
    
    @Test
    void testSubscribeType() {
        dynamicConfig = new CopilotDynamicConfig(agentManager);
        
        assertSame(ServerConfigChangeEvent.class, dynamicConfig.subscribeType());
    }
    
    @Test
    void testOnEventRefreshesConfiguration() {
        dynamicConfig = new CopilotDynamicConfig(agentManager);
        
        dynamicConfig.onEvent(ServerConfigChangeEvent.newEvent());
        
        verify(agentManager).refreshConfig();
    }
    
    @Test
    void testRegisteredSubscriberReceivesServerConfigChange() {
        dynamicConfig = new CopilotDynamicConfig(agentManager);
        dynamicConfig.afterPropertiesSet();
        
        NotifyCenter.publishEvent(ServerConfigChangeEvent.newEvent());
        
        verify(agentManager, timeout(3000)).refreshConfig();
    }
}
