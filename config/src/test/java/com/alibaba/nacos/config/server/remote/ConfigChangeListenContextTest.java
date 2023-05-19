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

package com.alibaba.nacos.config.server.remote;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class ConfigChangeListenContextTest {

    private ConfigChangeListenContext configChangeListenContext;

    @Before
    public void setUp() throws Exception {
        configChangeListenContext = new ConfigChangeListenContext();
    }

    @Test
    public void testAddListen() {
        configChangeListenContext.addListen("groupKey", "md5", "connectionId");
        Set<String> groupKey = configChangeListenContext.getListeners("groupKey");
        Assert.assertEquals(1, groupKey.size());
    }

    @Test
    public void testRemoveListen() {
        configChangeListenContext.addListen("groupKey", "md5", "connectionId");
        configChangeListenContext.removeListen("groupKey", "connectionId");
        Set<String> groupKey = configChangeListenContext.getListeners("groupKey");
        Assert.assertNull(groupKey);
    }

    @Test
    public void testGetListeners() {
        configChangeListenContext.addListen("groupKey", "md5", "connectionId");
        Set<String> groupKey = configChangeListenContext.getListeners("groupKey");
        Assert.assertEquals(1, groupKey.size());
    }

    @Test
    public void testClearContextForConnectionId() {
        configChangeListenContext.addListen("groupKey", "md5", "connectionId");
        Map<String, String> connectionIdBefore = configChangeListenContext.getListenKeys("connectionId");
        Assert.assertNotNull(connectionIdBefore);
        configChangeListenContext.clearContextForConnectionId("connectionId");
        Map<String, String> connectionIdAfter = configChangeListenContext.getListenKeys("connectionId");
        Assert.assertNull(connectionIdAfter);
    }

    @Test
    public void testGetListenKeys() {
        configChangeListenContext.addListen("groupKey", "md5", "connectionId");
        Set<String> groupKey = configChangeListenContext.getListeners("groupKey");
        Assert.assertEquals(1, groupKey.size());
    }

    @Test
    public void testGetListenKeyMd5() {
        configChangeListenContext.addListen("groupKey", "md5", "connectionId");
        String listenKeyMd5 = configChangeListenContext.getListenKeyMd5("connectionId", "groupKey");
        Assert.assertEquals("md5", listenKeyMd5);
    }

}