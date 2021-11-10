/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.ServiceDetailInfo;
import com.alibaba.nacos.naming.pojo.ServiceNameView;
import com.alibaba.nacos.naming.selector.SelectorManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ServiceControllerV2Test {
    
    @Mock
    private SelectorManager selectorManager;
    
    @Mock
    private ServiceOperatorV2Impl serviceOperatorV2;
    
    private ServiceControllerV2 serviceController;
    
    @Before
    public void setUp() throws Exception {
        serviceController = new ServiceControllerV2(serviceOperatorV2, selectorManager);
    }
    
    @Test
    public void testCreate() throws Exception {
        RestResult<String> actual = serviceController
                .create(Constants.DEFAULT_NAMESPACE_ID, "service", Constants.DEFAULT_GROUP, true, 0.0f, "", "");
        verify(serviceOperatorV2)
                .create(eq(Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service")),
                        any(ServiceMetadata.class));
        assertEquals("ok", actual.getData());
        assertEquals(200, actual.getCode());
    }
    
    @Test
    public void testRemove() throws Exception {
        RestResult<String> actual = serviceController
                .remove(Constants.DEFAULT_NAMESPACE_ID, "service", Constants.DEFAULT_GROUP);
        verify(serviceOperatorV2)
                .delete(Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service"));
        assertEquals("ok", actual.getData());
        assertEquals(200, actual.getCode());
    }
    
    @Test
    public void testDetail() throws Exception {
        ServiceDetailInfo expected = new ServiceDetailInfo();
        when(serviceOperatorV2
                .queryService(Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service")))
                .thenReturn(expected);
        RestResult<ServiceDetailInfo> actual = serviceController
                .detail(Constants.DEFAULT_NAMESPACE_ID, "service", Constants.DEFAULT_GROUP);
        assertEquals(200, actual.getCode());
        assertEquals(expected, actual.getData());
    }
    
    @Test
    public void testList() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getParameter("pageNo")).thenReturn("1");
        when(request.getParameter("pageSize")).thenReturn("10");
        when(serviceOperatorV2.listService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "")).thenReturn(
                Collections.singletonList("serviceName"));
        RestResult<ServiceNameView> actual = serviceController.list(request);
        assertEquals(200, actual.getCode());
        assertEquals(1, actual.getData().getCount());
        assertEquals(1, actual.getData().getServices().size());
        assertEquals("serviceName", actual.getData().getServices().iterator().next());
    }
    
    @Test
    public void testUpdate() throws Exception {
        RestResult<String> actual = serviceController
                .update(Constants.DEFAULT_NAMESPACE_ID, "service", Constants.DEFAULT_GROUP, 0.0f, "", "");
        verify(serviceOperatorV2)
                .update(eq(Service.newService(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "service")),
                        any(ServiceMetadata.class));
        assertEquals("ok", actual.getData());
        assertEquals(200, actual.getCode());
    }
}
