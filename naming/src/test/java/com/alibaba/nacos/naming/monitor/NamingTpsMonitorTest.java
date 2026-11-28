/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.monitor;

import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.plugin.control.context.ControlContext;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NamingTpsMonitorTest {
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    void testGetInstanceReturnsSingleton() {
        assertSame(NamingTpsMonitor.getInstance(), NamingTpsMonitor.getInstance());
    }
    
    @Test
    void testPushTaskAllowedByDefault() {
        assertTrue(NamingTpsMonitor.isPushTaskAllowed(
            Service.newService("namespace", "group", "service"), 10, 2));
    }
    
    @Test
    void testPushTaskCheckCarriesContext() {
        TpsControlManager tpsControlManager = Mockito.mock(TpsControlManager.class);
        when(tpsControlManager.check(any(TpsCheckRequest.class)))
            .thenReturn(new TpsCheckResponse(true, TpsResultCode.PASS_BY_POINT, "pass"));
        
        assertTrue(NamingTpsMonitor.isPushTaskAllowed(tpsControlManager,
            Service.newService("namespace", "group", "service"), 7, 3));
        
        ArgumentCaptor<TpsCheckRequest> captor =
            ArgumentCaptor.forClass(TpsCheckRequest.class);
        verify(tpsControlManager).check(captor.capture());
        TpsCheckRequest request = captor.getValue();
        assertEquals(TpsMonitorItem.NAMING_PUSH_TASK.name(), request.getPointName());
        assertEquals(7L,
            request.getControlContext().getMetric(ControlContext.PUSH_PENDING_TASKS));
        assertEquals(3L,
            request.getControlContext().getMetric(ControlContext.PUSH_TARGET_SUBSCRIBERS));
        assertEquals("namespace",
            request.getControlContext().getAttribute(ControlContext.PUSH_SERVICE_NAMESPACE));
        assertEquals("group",
            request.getControlContext().getAttribute(ControlContext.PUSH_SERVICE_GROUP));
        assertEquals("service",
            request.getControlContext().getAttribute(ControlContext.PUSH_SERVICE_NAME));
    }
    
    @Test
    void testPushTaskAllowedWhenPluginThrows() {
        TpsControlManager tpsControlManager = Mockito.mock(TpsControlManager.class);
        when(tpsControlManager.check(any(TpsCheckRequest.class)))
            .thenThrow(new IllegalStateException("plugin failure"));
        
        assertTrue(NamingTpsMonitor.isPushTaskAllowed(tpsControlManager,
            Service.newService("namespace", "group", "service"), 1, 1));
    }
}
