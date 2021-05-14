/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.distributed.distro.task.load;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.distro.DistroConfig;
import com.alibaba.nacos.core.distributed.distro.component.DistroCallback;
import com.alibaba.nacos.core.distributed.distro.component.DistroComponentHolder;
import com.alibaba.nacos.core.distributed.distro.component.DistroDataProcessor;
import com.alibaba.nacos.core.distributed.distro.component.DistroDataStorage;
import com.alibaba.nacos.core.distributed.distro.component.DistroFailedTaskHandler;
import com.alibaba.nacos.core.distributed.distro.component.DistroTransportAgent;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.sys.env.EnvUtil;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DistroLoadDataTaskTest extends TestCase {
    
    private final String type = "com.alibaba.nacos.naming.iplist.";
    
    private DistroComponentHolder componentHolder;
    
    @Mock
    private DistroDataStorage distroDataStorage;
    
    @Mock
    private DistroTransportAgent distroTransportAgent;
    
    @Mock
    private DistroFailedTaskHandler distroFailedTaskHandler;
    
    @Mock
    private DistroDataProcessor distroDataProcessor;
    
    @Mock
    private DistroData distroData;
    
    private DistroLoadDataTask distroLoadDataTask;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private DistroConfig distroConfig;
    
    @Mock
    private DistroCallback loadCallback;
    
    @Before
    public void setUp() throws Exception {
        EnvUtil.setEnvironment(new StandardEnvironment());
        memberManager = new ServerMemberManager(new MockServletContext());
        Member member1 = Member.builder().ip("2.2.2.2").port(8848).build();
        Member member2 = Member.builder().ip("1.1.1.1").port(8848).build();
        memberManager.update(member1);
        memberManager.update(member2);
        componentHolder = new DistroComponentHolder();
        componentHolder.registerDataStorage(type, distroDataStorage);
        componentHolder.registerTransportAgent(type, distroTransportAgent);
        componentHolder.registerFailedTaskHandler(type, distroFailedTaskHandler);
        when(distroDataProcessor.processType()).thenReturn(type);
        componentHolder.registerDataProcessor(distroDataProcessor);
        when(distroTransportAgent.getDatumSnapshot(any(String.class))).thenReturn(distroData);
        when(distroDataProcessor.processSnapshot(distroData)).thenReturn(true);
        distroLoadDataTask = new DistroLoadDataTask(memberManager, componentHolder, distroConfig, loadCallback);
    }
    
    @Test
    public void testRun() {
        distroLoadDataTask.run();
        Map<String, Boolean> loadCompletedMap = (Map<String, Boolean>) ReflectionTestUtils.getField(distroLoadDataTask, "loadCompletedMap");
        assertNotNull(loadCompletedMap);
        assertTrue(loadCompletedMap.containsKey(type));
        verify(distroTransportAgent).getDatumSnapshot(any(String.class));
    }
}