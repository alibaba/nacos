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

package com.alibaba.nacos.core.remote.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerReloadRequest;
import com.alibaba.nacos.api.remote.response.ServerReloadResponse;
import com.alibaba.nacos.core.remote.ConnectionManager;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * {@link ServerReloaderRequestHandler} unit test.
 *
 * @author chenglu
 * @date 2021-07-01 13:04
 */
@RunWith(MockitoJUnitRunner.class)
public class ServerReloaderRequestHandlerTest {
    
    @InjectMocks
    private ServerReloaderRequestHandler handler;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Test
    public void testHandle() {
        Mockito.when(connectionManager.currentClientsCount(Mockito.any())).thenReturn(2);
    
        ServerReloadRequest reloadRequest = new ServerReloadRequest();
        reloadRequest.setReloadCount(2);
        reloadRequest.setReloadServer("test");
        RequestMeta meta = new RequestMeta();
        meta.setClientIp("1.1.1.1");
        
        try {
            ServerReloadResponse reloadResponse = handler.handle(reloadRequest, meta);
            Assert.assertEquals("ignore", reloadResponse.getMessage());
        } catch (NacosException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        
        reloadRequest.setReloadCount(1);
        try {
            ServerReloadResponse reloadResponse = handler.handle(reloadRequest, meta);
            Assert.assertEquals("ok", reloadResponse.getMessage());
        } catch (NacosException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
}
