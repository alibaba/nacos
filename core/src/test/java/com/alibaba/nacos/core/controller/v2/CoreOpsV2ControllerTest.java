/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.controller.v2;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.consistency.IdGenerator;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.alibaba.nacos.core.distributed.id.SnowFlowerIdGenerator;
import com.alibaba.nacos.core.model.request.LogUpdateRequest;
import com.alibaba.nacos.core.model.vo.IdGeneratorVO;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CoreOpsV2ControllerTest {
    
    private final MockEnvironment mockEnvironment = new MockEnvironment();
    
    @InjectMocks
    private CoreOpsV2Controller coreOpsV2Controller;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private IdGeneratorManager idGeneratorManager;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(mockEnvironment);
    }
    
    @Test
    void testRaftOps() {
        Mockito.when(protocolManager.getCpProtocol()).thenAnswer(invocationOnMock -> {
            CPProtocol cpProtocol = Mockito.mock(CPProtocol.class);
            Mockito.when(cpProtocol.execute(Mockito.anyMap())).thenReturn(RestResultUtils.success("res"));
            return cpProtocol;
        });
        
        RestResult<String> result = coreOpsV2Controller.raftOps(new HashMap<>());
        assertEquals("res", result.getData());
    }
    
    @Test
    void testIdInfo() {
        mockEnvironment.setProperty("nacos.core.snowflake.worker-id", "1");
        
        Map<String, IdGenerator> idGeneratorMap = new HashMap<>();
        idGeneratorMap.put("resource", new SnowFlowerIdGenerator());
        Mockito.when(idGeneratorManager.getGeneratorMap()).thenReturn(idGeneratorMap);
        RestResult<List<IdGeneratorVO>> res = coreOpsV2Controller.ids();
        
        assertTrue(res.ok());
        assertEquals(1, res.getData().size());
        assertEquals("resource", res.getData().get(0).getResource());
        assertEquals(1L, res.getData().get(0).getInfo().getWorkerId().longValue());
        assertEquals(0L, res.getData().get(0).getInfo().getCurrentId().longValue());
    }
    
    @Test
    void testSetLogLevel() {
        LogUpdateRequest request = new LogUpdateRequest();
        request.setLogName("core");
        request.setLogLevel("debug");
        RestResult<?> res = coreOpsV2Controller.updateLog(request);
        
        assertTrue(res.ok());
        assertTrue(Loggers.CORE.isDebugEnabled());
    }
}
