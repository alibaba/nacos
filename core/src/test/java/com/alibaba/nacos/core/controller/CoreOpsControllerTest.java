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

package com.alibaba.nacos.core.controller;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.consistency.IdGenerator;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.alibaba.nacos.core.distributed.id.SnowFlowerIdGenerator;
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
import java.util.Map;

/**
 * {@link CoreOpsController} unit test.
 *
 * @author chenglu
 * @date 2021-07-07 22:20
 */
@RunWith(MockitoJUnitRunner.class)
public class CoreOpsControllerTest {
    
    @InjectMocks
    private CoreOpsController coreOpsController;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private IdGeneratorManager idGeneratorManager;
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new MockEnvironment());
    }
    
    @Test
    public void testRaftOps() {
        Mockito.when(protocolManager.getCpProtocol()).thenAnswer(invocationOnMock -> {
            CPProtocol cpProtocol = Mockito.mock(CPProtocol.class);
            Mockito.when(cpProtocol.execute(Mockito.anyMap())).thenReturn(RestResultUtils.success("res"));
            return cpProtocol;
        });
    
        RestResult<String> result = coreOpsController.raftOps(new HashMap<>());
        Assert.assertEquals("res", result.getData());
    }
    
    @Test
    public void testIdInfo() {
        Map<String, IdGenerator> idGeneratorMap = new HashMap<>();
        idGeneratorMap.put("1", new SnowFlowerIdGenerator());
        Mockito.when(idGeneratorManager.getGeneratorMap()).thenReturn(idGeneratorMap);
    
        RestResult<Map<String, Map<Object, Object>>> res = coreOpsController.idInfo();
        Assert.assertEquals(2, res.getData().get("1").size());
    }
    
    @Test
    public void testSetLogLevel() {
        String res = coreOpsController.setLogLevel("1", "info");
        Assert.assertEquals("200", res);
    }
}
