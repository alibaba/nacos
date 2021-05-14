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

package com.alibaba.nacos.naming.consistency.ephemeral.distro;

import com.alibaba.nacos.naming.consistency.Datum;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class DataStoreTest extends TestCase {
    
    private DataStore dataStore;
    
    private String key;
    
    @Mock
    private Datum datum;
    
    @Mock
    private Map<String, Datum> dataMap;
    
    @Before
    public void setUp() {
        dataStore = new DataStore();
        key = "tmp_key";
        ReflectionTestUtils.setField(dataStore, "dataMap", dataMap);
    }
    
    @Test
    public void testPut() {
        dataStore.put(key, datum);
        verify(dataMap).put(key, datum);
    }
    
    @Test
    public void testRemove() {
        dataStore.remove(key);
        verify(dataMap).remove(key);
    }
    
    @Test
    public void testKeys() {
        dataStore.keys();
        verify(dataMap).keySet();
    }
    
    @Test
    public void testGet() {
        dataStore.get(key);
        verify(dataMap).get(key);
    }
    
    @Test
    public void testContains() {
        dataStore.contains(key);
        verify(dataMap).containsKey(key);
    }
}