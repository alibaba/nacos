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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.cluster.RemoteServerMemberManager;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerService;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosMaintainerClientHolderTest {
    
    @Mock
    RemoteServerMemberManager memberManager;
    
    ConfigurableEnvironment cachedEnvironment;
    
    NacosMaintainerClientHolder maintainerClientHolder;
    
    @BeforeEach
    void setUp() throws NacosException {
        cachedEnvironment = EnvUtil.getEnvironment();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        EnvUtil.setEnvironment(environment);
        Member member = new Member();
        member.setIp("127.0.0.1");
        member.setPort(3306);
        when(memberManager.allMembers()).thenReturn(Collections.singletonList(member));
        maintainerClientHolder = new NacosMaintainerClientHolder(memberManager);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void onEvent() {
        NamingMaintainerService namingMaintainerService = maintainerClientHolder.getNamingMaintainerService();
        ConfigMaintainerService configMaintainerService = maintainerClientHolder.getConfigMaintainerService();
        AiMaintainerService aiMaintainerService = maintainerClientHolder.getAiMaintainerService();
        assertNotNull(namingMaintainerService);
        assertNotNull(configMaintainerService);
        assertNotNull(aiMaintainerService);
        maintainerClientHolder.onEvent(MembersChangeEvent.builder().build());
        assertNotNull(maintainerClientHolder.getNamingMaintainerService());
        assertNotNull(maintainerClientHolder.getConfigMaintainerService());
        assertNotNull(maintainerClientHolder.getAiMaintainerService());
        assertNotEquals(namingMaintainerService, maintainerClientHolder.getNamingMaintainerService());
        assertNotEquals(configMaintainerService, maintainerClientHolder.getConfigMaintainerService());
        assertNotEquals(aiMaintainerService, maintainerClientHolder.getAiMaintainerService());
    }
    
    @Test
    void onEventWithException() {
        NamingMaintainerService namingMaintainerService = maintainerClientHolder.getNamingMaintainerService();
        ConfigMaintainerService configMaintainerService = maintainerClientHolder.getConfigMaintainerService();
        AiMaintainerService aiMaintainerService = maintainerClientHolder.getAiMaintainerService();
        assertNotNull(namingMaintainerService);
        assertNotNull(configMaintainerService);
        assertNotNull(aiMaintainerService);
        when(memberManager.allMembers()).thenReturn(Collections.emptyList());
        maintainerClientHolder.onEvent(MembersChangeEvent.builder().build());
        assertNotNull(maintainerClientHolder.getNamingMaintainerService());
        assertNotNull(maintainerClientHolder.getConfigMaintainerService());
        assertNotNull(maintainerClientHolder.getAiMaintainerService());
        assertEquals(namingMaintainerService, maintainerClientHolder.getNamingMaintainerService());
        assertEquals(configMaintainerService, maintainerClientHolder.getConfigMaintainerService());
        assertEquals(aiMaintainerService, maintainerClientHolder.getAiMaintainerService());
    }
}