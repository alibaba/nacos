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

import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.core.ServerLoaderInfoRequestHandler;
import com.alibaba.nacos.core.remote.core.ServerReloaderRequestHandler;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link ServerLoaderController} unit test.
 *
 * @author chenglu
 * @date 2021-07-07 23:10
 */
@RunWith(MockitoJUnitRunner.class)
public class ServerLoaderControllerTest {
    
    @InjectMocks
    private ServerLoaderController serverLoaderController;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    @Mock
    private ClusterRpcClientProxy clusterRpcClientProxy;
    
    @Mock
    private ServerReloaderRequestHandler serverReloaderRequestHandler;
    
    @Mock
    private ServerLoaderInfoRequestHandler serverLoaderInfoRequestHandler;
    
    @Test
    public void testCurrentClients() {
        Mockito.when(connectionManager.currentClients()).thenReturn(new HashMap<>());
    
        ResponseEntity<Map<String, Connection>> result = serverLoaderController.currentClients();
        Assert.assertEquals(0, result.getBody().size());
    }
    
    @Test
    public void testReloadCount() {
        ResponseEntity<String> result = serverLoaderController.reloadCount(1, "1.1.1.1");
        Assert.assertEquals("success", result.getBody());
    }
}
