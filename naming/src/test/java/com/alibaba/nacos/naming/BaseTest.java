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
package com.alibaba.nacos.naming;

import com.alibaba.nacos.naming.boot.SpringContext;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftCore;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeer;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeerSet;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.healthcheck.HealthCheckProcessorDelegate;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.push.PushService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.context.ApplicationContext;

import static org.mockito.Mockito.doReturn;

/**
 * @author nkorange
 */
public class BaseTest {

    @Mock
    public ServiceManager serviceManager;

    @Mock
    public RaftPeerSet peerSet;

    @Mock
    public RaftCore raftCore;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        RaftPeer peer = new RaftPeer();
        peer.ip = NetUtils.localServer();
        raftCore.setPeerSet(peerSet);
        Mockito.when(peerSet.local()).thenReturn(peer);
        Mockito.when(peerSet.getLeader()).thenReturn(peer);
        Mockito.when(peerSet.isLeader(NetUtils.localServer())).thenReturn(true);

        new SpringContext().setApplicationContext(context);
        doReturn(distroMapper).when(context).getBean(DistroMapper.class);
        doReturn(switchDomain).when(context).getBean(SwitchDomain.class);
        doReturn(delegate).when(context).getBean(HealthCheckProcessorDelegate.class);
        doReturn(pushService).when(context).getBean(PushService.class);
    }

    protected static final String TEST_CLUSTER_NAME = "test-cluster";
    protected static final String TEST_SERVICE_NAME = "test-service";
    protected static final String TEST_GROUP_NAME = "test-group-name";
    protected static final String TEST_NAMESPACE = "test-namespace";

    @Spy
    protected ApplicationContext context;
    @Mock
    protected DistroMapper distroMapper;
    @Spy
    protected SwitchDomain switchDomain;
    @Mock
    protected HealthCheckProcessorDelegate delegate;
    @Mock
    protected PushService pushService;
}
