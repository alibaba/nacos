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

package com.alibaba.nacos.naming.consistency;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.ephemeral.EphemeralConsistencyService;
import com.alibaba.nacos.naming.consistency.persistent.PersistentConsistencyServiceDelegateImpl;
import com.alibaba.nacos.naming.pojo.Record;
import junit.framework.TestCase;
import org.junit.Before;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DelegateConsistencyServiceImplTest extends TestCase {
    
    private DelegateConsistencyServiceImpl delegateConsistencyService;
    
    @Mock
    private PersistentConsistencyServiceDelegateImpl persistentConsistencyService;
    
    @Mock
    private EphemeralConsistencyService ephemeralConsistencyService;
    
    private static final String EPHEMERAL_KEY_PREFIX = "ephemeral.";
    
    public static final String INSTANCE_LIST_KEY_PREFIX = "com.alibaba.nacos.naming.iplist.";
    
    private final String ephemeralPrefix = INSTANCE_LIST_KEY_PREFIX + EPHEMERAL_KEY_PREFIX;
    
    @Mock
    private Record record;
    
    private String ephemeralKey;
    
    private String peristentKey;
    
    public DelegateConsistencyServiceImplTest() {
    }
    
    @Before
    public void setUp() {
        delegateConsistencyService
                = new DelegateConsistencyServiceImpl(persistentConsistencyService, ephemeralConsistencyService);
        ephemeralKey = ephemeralPrefix + "test-key";
        peristentKey = "persistent-test-key";
    }
    
    @Test
    public void testPut() throws NacosException {
        delegateConsistencyService.put(ephemeralKey, record);
        verify(ephemeralConsistencyService).put(ephemeralKey, record);
        verify(persistentConsistencyService, never()).put(ephemeralKey, record);
    
        delegateConsistencyService.put(peristentKey, record);
        verify(persistentConsistencyService).put(peristentKey, record);
    }
    
    @Test
    public void testRemove() throws NacosException {
        delegateConsistencyService.remove(ephemeralKey);
        verify(ephemeralConsistencyService).remove(ephemeralKey);
        verify(persistentConsistencyService, never()).remove(ephemeralKey);
    
        delegateConsistencyService.remove(peristentKey);
        verify(persistentConsistencyService).remove(peristentKey);
    }
    
    @Test
    public void testGet() throws NacosException {
        delegateConsistencyService.get(ephemeralKey);
        verify(ephemeralConsistencyService).get(ephemeralKey);
        verify(persistentConsistencyService, never()).get(ephemeralKey);
    
        delegateConsistencyService.get(peristentKey);
        verify(persistentConsistencyService).get(peristentKey);
    }
    
    @Test
    public void testListen() throws NacosException {
        delegateConsistencyService.listen(ephemeralKey, null);
        verify(ephemeralConsistencyService).listen(ephemeralKey, null);
        verify(persistentConsistencyService, never()).listen(ephemeralKey, null);
    
        delegateConsistencyService.listen(peristentKey, null);
        verify(persistentConsistencyService).listen(peristentKey, null);
    }
    
    @Test
    public void testUnListen() throws NacosException {
        delegateConsistencyService.unListen(ephemeralKey, null);
        verify(ephemeralConsistencyService).unListen(ephemeralKey, null);
        verify(persistentConsistencyService, never()).unListen(ephemeralKey, null);
    
        delegateConsistencyService.unListen(peristentKey, null);
        verify(persistentConsistencyService).unListen(peristentKey, null);
    }
    
    @Test
    public void testIsAvailable() {
        delegateConsistencyService.isAvailable();
        verify(ephemeralConsistencyService).isAvailable();
        verify(persistentConsistencyService, never()).isAvailable();
    }
    
    @Test
    public void testGetErrorMsg() {
        int ephemeralCalledTimes = 3;
        delegateConsistencyService.getErrorMsg();
        verify(ephemeralConsistencyService, times(ephemeralCalledTimes)).getErrorMsg();
        verify(persistentConsistencyService).getErrorMsg();
    }
    
}