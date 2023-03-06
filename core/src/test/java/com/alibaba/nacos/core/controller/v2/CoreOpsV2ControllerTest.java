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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class CoreOpsV2ControllerTest {
    
    @InjectMocks
    private CoreOpsV2Controller coreOpsV2Controller;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private IdGeneratorManager idGeneratorManager;
    
    private final MockEnvironment mockEnvironment = new MockEnvironment();
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(mockEnvironment);
    }
    
    @Test
    public void testRaftOps() {
        Mockito.when(protocolManager.getCpProtocol()).thenAnswer(invocationOnMock -> {
            CPProtocol cpProtocol = Mockito.mock(CPProtocol.class);
            Mockito.when(cpProtocol.execute(Mockito.anyMap())).thenReturn(RestResultUtils.success("res"));
            return cpProtocol;
        });
        
        RestResult<String> result = coreOpsV2Controller.raftOps(new HashMap<>());
        Assert.assertEquals("res", result.getData());
    }
    
    @Test
    public void testIdInfo() {
        mockEnvironment.setProperty("nacos.core.snowflake.worker-id", "1");
        
        Map<String, IdGenerator> idGeneratorMap = new HashMap<>();
        idGeneratorMap.put("resource", new SnowFlowerIdGenerator());
        Mockito.when(idGeneratorManager.getGeneratorMap()).thenReturn(idGeneratorMap);
        RestResult<List<IdGeneratorVO>> res = coreOpsV2Controller.ids();
    
        Assert.assertTrue(res.ok());
        Assert.assertEquals(1, res.getData().size());
        Assert.assertEquals("resource", res.getData().get(0).getResource());
        Assert.assertEquals(1L, res.getData().get(0).getInfo().getWorkerId().longValue());
        Assert.assertEquals(0L, res.getData().get(0).getInfo().getCurrentId().longValue());
    }
    
    @Test
    public void testSetLogLevel() {
        LogUpdateRequest request = new LogUpdateRequest();
        request.setLogName("core");
        request.setLogLevel("debug");
        RestResult<?> res = coreOpsV2Controller.updateLog(request);
    
        Assert.assertTrue(res.ok());
        Assert.assertTrue(Loggers.CORE.isDebugEnabled());
    }
}
