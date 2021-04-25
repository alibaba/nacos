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

package com.alibaba.nacos.naming.consistency.persistent;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftConsistencyServiceImpl;
import com.alibaba.nacos.naming.pojo.Record;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PersistentConsistencyServiceDelegateImplTest {
    
    @Mock
    private ClusterVersionJudgement clusterVersionJudgement;
    
    @Mock
    private RaftConsistencyServiceImpl raftConsistencyService;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private Record record;
    
    @Mock
    private RecordListener recordListener;
    
    private PersistentConsistencyServiceDelegateImpl persistentConsistencyServiceDelegate;
    
    
    @Before
    public void setUp() throws Exception {
        persistentConsistencyServiceDelegate = new PersistentConsistencyServiceDelegateImpl(clusterVersionJudgement,
                raftConsistencyService, protocolManager);
    }
    
    
    @Test
    public void testPut() throws Exception {
        String key = "record_key";
        persistentConsistencyServiceDelegate.put(key, record);
        Mockito.verify(raftConsistencyService).put(key, record);
    }
    
    @Test
    public void testRemove() throws NacosException {
        String key = "record_key";
        persistentConsistencyServiceDelegate.remove(key);
        Mockito.verify(raftConsistencyService).remove(key);
    }
    
    @Test
    public void testGet() throws NacosException {
        String key = "record_key";
        persistentConsistencyServiceDelegate.get(key);
        Mockito.verify(raftConsistencyService).get(key);
    }
    
    @Test
    public void testListen() throws NacosException {
        String key = "listen_key";
        persistentConsistencyServiceDelegate.listen(key, recordListener);
        Mockito.verify(raftConsistencyService).listen(key, recordListener);
    }
    
    @Test
    public void testUnListen() throws NacosException {
        String key = "listen_key";
        persistentConsistencyServiceDelegate.unListen(key, recordListener);
        Mockito.verify(raftConsistencyService).unListen(key, recordListener);
    }
    
    @Test
    public void testIsAvailable() {
        persistentConsistencyServiceDelegate.isAvailable();
        Mockito.verify(raftConsistencyService).isAvailable();
    }
    
    @Test
    public void testGetErrorMsg() {
        persistentConsistencyServiceDelegate.getErrorMsg();
        Mockito.verify(raftConsistencyService).getErrorMsg();
    }
}
