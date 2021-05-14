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

package com.alibaba.nacos.core.distributed.distro.task.verify;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.distro.component.DistroComponentHolder;
import com.alibaba.nacos.core.distributed.distro.component.DistroDataStorage;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DistroVerifyTaskTest extends TestCase {
    
    private DistroComponentHolder componentHolder;
    
    private final String type = "com.alibaba.nacos.naming.iplist.";
    
    @Mock
    private DistroTransportAgent distroTransportAgent;
    
    @Mock
    private DistroDataStorage distroDataStorage;
    
    private DistroVerifyTask distroVerifyTask;
    
    private ServerMemberManager serverMemberManager;
    
    @Mock
    private DistroData distroData;
    
    @Before
    public void setUp() throws Exception {
        EnvUtil.setEnvironment(new StandardEnvironment());
        
        componentHolder = new DistroComponentHolder();
        componentHolder.registerDataStorage(type, distroDataStorage);
        when(distroDataStorage.getVerifyData()).thenReturn(distroData);
        componentHolder.registerTransportAgent(type, distroTransportAgent);
    
        serverMemberManager = new ServerMemberManager(new MockServletContext());
        distroVerifyTask = new DistroVerifyTask(serverMemberManager, componentHolder);
        
    }
    
    @Test
    public void testRun() {
        distroVerifyTask.run();
        List<Member> targetServer = serverMemberManager.allMembersWithoutSelf();
        DistroData distroData = componentHolder.findDataStorage(type).getVerifyData();
        for (Member member : targetServer) {
            verify(distroTransportAgent).syncVerifyData(eq(distroData), eq(member.getAddress()));
        }
    }
}