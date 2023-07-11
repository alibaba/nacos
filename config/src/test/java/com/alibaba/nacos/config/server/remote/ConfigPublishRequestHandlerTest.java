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

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.StandardEnvironment;

@RunWith(MockitoJUnitRunner.class)
public class ConfigPublishRequestHandlerTest {
    
    private ConfigPublishRequestHandler configPublishRequestHandler;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigInfoTagPersistService configInfoTagPersistService;
    
    @Mock
    private ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    @Before
    public void setUp() {
        configPublishRequestHandler = new ConfigPublishRequestHandler(configInfoPersistService,
                configInfoTagPersistService, configInfoBetaPersistService);
        EnvUtil.setEnvironment(new StandardEnvironment());
    }
    
    @Test
    public void testHandle() throws NacosException {
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setDataId("dataId");
        configPublishRequest.setGroup("group");
        configPublishRequest.setContent("content");
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        ConfigPublishResponse response = configPublishRequestHandler.handle(configPublishRequest, requestMeta);
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
    }
}