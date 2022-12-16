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

import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfigRemoveRequestHandlerTest {

    private ConfigRemoveRequestHandler configRemoveRequestHandler;

    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigInfoTagPersistService configInfoTagPersistService;

    @Before
    public void setUp() throws Exception {
        configRemoveRequestHandler = new ConfigRemoveRequestHandler(configInfoPersistService,
                configInfoTagPersistService);
        Mockito.mockStatic(ConfigTraceService.class);
    }

    @Test
    public void testHandle() {
        ConfigRemoveRequest configRemoveRequest = new ConfigRemoveRequest();
        configRemoveRequest.setRequestId("requestId");
        configRemoveRequest.setGroup("group");
        configRemoveRequest.setDataId("dataId");
        configRemoveRequest.setTenant("tenant");
        RequestMeta meta = new RequestMeta();
        meta.setClientIp("1.1.1.1");
        try {
            ConfigRemoveResponse configRemoveResponse = configRemoveRequestHandler.handle(configRemoveRequest, meta);
            Assert.assertEquals(ResponseCode.SUCCESS.getCode(), configRemoveResponse.getResultCode());
        } catch (NacosException e) {
            e.printStackTrace();
        }
    }

}