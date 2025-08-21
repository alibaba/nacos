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

package com.alibaba.nacos.naming.controllers.v3;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.maintainer.MetricsInfo;
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.core.Operator;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.model.form.UpdateSwitchForm;
import com.alibaba.nacos.naming.model.vo.MetricsInfoVo;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doNothing;

/**
 * OperatorControllerV3Test.
 *
 * @author Nacos
 */

@ExtendWith(MockitoExtension.class)
class OperatorControllerV3Test {
    
    private OperatorControllerV3 operatorControllerV3;
    
    @Mock
    private Operator operatorV2Impl;
    
    @BeforeEach
    void setUp() {
        this.operatorControllerV3 = new OperatorControllerV3(operatorV2Impl);
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }
    
    @Test
    void testSwitches() {
        SwitchDomain switchDomain = new SwitchDomain();
        switchDomain.setDefaultInstanceEphemeral(true);
        switchDomain.setDefaultPushCacheMillis(1000L);
        Mockito.when(operatorV2Impl.switches()).thenReturn(switchDomain);
        Result<SwitchDomain> result = operatorControllerV3.switches();
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1000L, result.getData().getDefaultPushCacheMillis());
        assertEquals(true, result.getData().isDefaultInstanceEphemeral());
    }
    
    @Test
    void testUpdateSwitches() {
        UpdateSwitchForm updateSwitchForm = new UpdateSwitchForm();
        updateSwitchForm.setDebug(true);
        updateSwitchForm.setEntry("test");
        updateSwitchForm.setValue("test");
        
        try {
            Result<String> result = operatorControllerV3.updateSwitch(updateSwitchForm);
            assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
            assertEquals("ok", result.getData());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    @Test
    void testMetrics() {
        MetricsInfoVo metricsInfoVo = new MetricsInfoVo();
        metricsInfoVo.setStatus(ServerStatus.UP.toString());
        Mockito.when(operatorV2Impl.metrics(false)).thenReturn(metricsInfoVo);
        Result<MetricsInfo> result = operatorControllerV3.metrics(false);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(ServerStatus.UP.toString(), result.getData().getStatus());
    }
    
    @Test
    void testLog() {
        doNothing().when(operatorV2Impl).setLogLevel("test", "test");
        Result<String> result = operatorControllerV3.setLogLevel("test", "test");
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
    }
}
