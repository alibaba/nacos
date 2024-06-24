/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v2;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.SwitchManager;
import com.alibaba.nacos.naming.model.form.UpdateSwitchForm;
import com.alibaba.nacos.naming.model.vo.MetricsInfoVo;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collection;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * OperatorControllerV2Test.
 *
 * @author dongyafei
 * @date 2022/9/15
 */

@ExtendWith(MockitoExtension.class)
class OperatorControllerV2Test {
    
    private OperatorControllerV2 operatorControllerV2;
    
    @Mock
    private SwitchDomain switchDomain;
    
    @Mock
    private SwitchManager switchManager;
    
    @Mock
    private ServerStatusManager serverStatusManager;
    
    @Mock
    private ClientManager clientManager;
    
    @BeforeEach
    void setUp() {
        this.operatorControllerV2 = new OperatorControllerV2(switchManager, serverStatusManager, switchDomain, clientManager);
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Constants.SUPPORT_UPGRADE_FROM_1X, "true");
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    void testSwitches() {
        Result<SwitchDomain> result = operatorControllerV2.switches();
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(this.switchDomain, result.getData());
    }
    
    @Test
    void testUpdateSwitches() {
        UpdateSwitchForm updateSwitchForm = new UpdateSwitchForm();
        updateSwitchForm.setDebug(true);
        updateSwitchForm.setEntry("test");
        updateSwitchForm.setValue("test");
        
        try {
            Result<String> result = operatorControllerV2.updateSwitch(updateSwitchForm);
            assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
            assertEquals("ok", result.getData());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    @Test
    void testMetrics() {
        Mockito.when(serverStatusManager.getServerStatus()).thenReturn(ServerStatus.UP);
        Collection<String> clients = new HashSet<>();
        clients.add("1628132208793_127.0.0.1_8080");
        clients.add("127.0.0.1:8081#true");
        clients.add("127.0.0.1:8082#false");
        Mockito.when(clientManager.allClientId()).thenReturn(clients);
        Mockito.when(clientManager.isResponsibleClient(null)).thenReturn(Boolean.TRUE);
        
        Result<MetricsInfoVo> result = operatorControllerV2.metrics(false);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        
        MetricsInfoVo metricsInfoVo = result.getData();
        
        assertEquals(ServerStatus.UP.toString(), metricsInfoVo.getStatus());
        assertEquals(3, metricsInfoVo.getClientCount().intValue());
        assertEquals(1, metricsInfoVo.getConnectionBasedClientCount().intValue());
        assertEquals(1, metricsInfoVo.getEphemeralIpPortClientCount().intValue());
        assertEquals(1, metricsInfoVo.getPersistentIpPortClientCount().intValue());
        assertEquals(3, metricsInfoVo.getResponsibleClientCount().intValue());
    }
}
