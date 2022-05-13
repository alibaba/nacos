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

package com.alibaba.nacos.naming.raft;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.persistent.ClusterVersionJudgement;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftCore;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftStore;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.sys.env.Constants;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class RaftStoreTest extends BaseTest {
    
    public RaftCore raftCore;
    
    public RaftStore raftStore;
    
    @Mock
    private ClusterVersionJudgement versionJudgement;
    
    @Before
    public void setUp() {
        super.before();
        environment.setProperty(Constants.SUPPORT_UPGRADE_FROM_1X, "true");
        raftStore = new RaftStore();
        raftCore = new RaftCore(peerSet, switchDomain, null, null, raftStore, versionJudgement, null);
    }
    
    @After
    public void tearDown() throws NacosException {
        raftCore.shutdown();
        raftStore.shutdown();
    }
    
    @Test
    public void wrietDatum() throws Exception {
        Datum<Instances> datum = new Datum<>();
        String key = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, false);
        datum.key = key;
        datum.timestamp.getAndIncrement();
        datum.value = new Instances();
        Instance instance = new Instance("1.1.1.1", 1, TEST_CLUSTER_NAME);
        datum.value.getInstanceList().add(instance);
        instance = new Instance("2.2.2.2", 2, TEST_CLUSTER_NAME);
        datum.value.getInstanceList().add(instance);
        
        raftStore.write(datum);
        raftCore.init();
        Datum result = raftCore.getDatum(key);
        
        Assert.assertEquals(key, result.key);
        Assert.assertEquals(1, result.timestamp.intValue());
        Assert.assertEquals(datum.value.toString(), result.value.toString());
    }
}
