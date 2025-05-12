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

package com.alibaba.nacos.console.handler.impl.remote;

import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractRemoteHandlerTest {
    
    @Mock
    protected NacosMaintainerClientHolder clientHolder;
    
    @Mock
    protected NamingMaintainerService namingMaintainerService;
    
    @Mock
    protected ConfigMaintainerService configMaintainerService;
    
    @Mock
    protected AiMaintainerService aiMaintainerService;
    
    protected void setUpWithNaming() {
        when(clientHolder.getNamingMaintainerService()).thenReturn(namingMaintainerService);
    }
    
    protected void setUpWithConfig() {
        when(clientHolder.getConfigMaintainerService()).thenReturn(configMaintainerService);
    }
    
    protected void setUpWithAi() {
        when(clientHolder.getAiMaintainerService()).thenReturn(aiMaintainerService);
    }
}
