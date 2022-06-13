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

package com.alibaba.nacos.naming.remote.rpc.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.request.ServiceListRequest;
import com.alibaba.nacos.api.naming.remote.response.ServiceListResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link ServiceListRequestHandler} unit tests.
 *
 * @author chenglu
 * @date 2021-09-17 20:59
 */
public class ServiceListRequestHandlerTest {
    
    private Service service;
    
    @Before
    public void setUp() {
        service = Service.newService("A", "B", "C");
        ServiceManager.getInstance().getSingleton(service);
    }
    
    @After
    public void tearDown() {
        ServiceManager.getInstance().removeSingleton(service);
    }
    
    @Test
    public void testHandle() throws NacosException {
        ServiceListRequest serviceListRequest = new ServiceListRequest();
        serviceListRequest.setNamespace("A");
        serviceListRequest.setPageNo(1);
        serviceListRequest.setPageSize(10);
        serviceListRequest.setGroupName("B");
        ServiceListRequestHandler serviceListRequestHandler = new ServiceListRequestHandler();
        ServiceListResponse serviceListResponse = serviceListRequestHandler.handle(serviceListRequest, new RequestMeta());
        Assert.assertEquals(serviceListResponse.getCount(), 1);
        Assert.assertTrue(serviceListResponse.getServiceNames().contains("C"));
    }
}
