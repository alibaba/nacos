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
import com.alibaba.nacos.naming.consistency.persistent.impl.BasePersistentServiceProcessor;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftConsistencyServiceImpl;
import com.alibaba.nacos.naming.pojo.Record;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;

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
    
    @Mock
    private BasePersistentServiceProcessor basePersistentServiceProcessor;
    
    private PersistentConsistencyServiceDelegateImpl oldPersistentConsistencyServiceDelegate;
    
    private PersistentConsistencyServiceDelegateImpl newPersistentConsistencyServiceDelegate;
    
    @Before
    public void setUp() throws Exception {
        oldPersistentConsistencyServiceDelegate = new PersistentConsistencyServiceDelegateImpl(clusterVersionJudgement,
                raftConsistencyService, protocolManager);
        
        newPersistentConsistencyServiceDelegate = new PersistentConsistencyServiceDelegateImpl(clusterVersionJudgement,
                raftConsistencyService, protocolManager);
        Class<PersistentConsistencyServiceDelegateImpl> persistentConsistencyServiceDelegateClass = PersistentConsistencyServiceDelegateImpl.class;
        Field switchNewPersistentService = persistentConsistencyServiceDelegateClass
                .getDeclaredField("switchNewPersistentService");
        switchNewPersistentService.setAccessible(true);
        switchNewPersistentService.set(newPersistentConsistencyServiceDelegate, true);
    
        Field newPersistentConsistencyService = persistentConsistencyServiceDelegateClass
                .getDeclaredField("newPersistentConsistencyService");
        newPersistentConsistencyService.setAccessible(true);
        newPersistentConsistencyService.set(newPersistentConsistencyServiceDelegate, basePersistentServiceProcessor);
    }
    
    @Test()
    public void testPut() throws Exception {
        String key = "record_key";
        oldPersistentConsistencyServiceDelegate.put(key, record);
        Mockito.verify(raftConsistencyService).put(key, record);
        
        newPersistentConsistencyServiceDelegate.put(key, record);
        Mockito.verify(basePersistentServiceProcessor).put(key, record);
    }
    
    @Test
    public void testRemove() throws NacosException {
        String key = "record_key";
        oldPersistentConsistencyServiceDelegate.remove(key);
        Mockito.verify(raftConsistencyService).remove(key);
        
        newPersistentConsistencyServiceDelegate.remove(key);
        Mockito.verify(basePersistentServiceProcessor).remove(key);
    }
    
    @Test()
    public void testGet() throws NacosException {
        String key = "record_key";
        oldPersistentConsistencyServiceDelegate.get(key);
        Mockito.verify(raftConsistencyService).get(key);
        
        newPersistentConsistencyServiceDelegate.get(key);
        Mockito.verify(basePersistentServiceProcessor).get(key);
    }
    
    @Test
    public void testListen() throws NacosException {
        String key = "listen_key";
        oldPersistentConsistencyServiceDelegate.listen(key, recordListener);
        Mockito.verify(raftConsistencyService).listen(key, recordListener);
        
        newPersistentConsistencyServiceDelegate.listen(key, recordListener);
        Mockito.verify(basePersistentServiceProcessor).listen(key, recordListener);
    }
    
    @Test
    public void testUnListen() throws NacosException {
        String key = "listen_key";
        oldPersistentConsistencyServiceDelegate.unListen(key, recordListener);
        Mockito.verify(raftConsistencyService).unListen(key, recordListener);
        
        newPersistentConsistencyServiceDelegate.unListen(key, recordListener);
        Mockito.verify(basePersistentServiceProcessor).unListen(key, recordListener);
    }
    
    @Test
    public void testIsAvailable() {
        oldPersistentConsistencyServiceDelegate.isAvailable();
        Mockito.verify(raftConsistencyService).isAvailable();
    
        newPersistentConsistencyServiceDelegate.isAvailable();
        Mockito.verify(basePersistentServiceProcessor).isAvailable();
    }
    
    @Test
    public void testGetErrorMsg() {
        oldPersistentConsistencyServiceDelegate.getErrorMsg();
        Mockito.verify(raftConsistencyService).getErrorMsg();
    
        newPersistentConsistencyServiceDelegate.getErrorMsg();
        Mockito.verify(basePersistentServiceProcessor).getErrorMsg();
    }
}
