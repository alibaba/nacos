/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v2;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogControllerV2Test extends BaseTest {
    
    List instances;
    
    @Mock
    private CatalogServiceV2Impl catalogServiceV2;
    
    @InjectMocks
    private CatalogControllerV2 catalogControllerV2;
    
    private MockMvc mockmvc;
    
    @BeforeEach
    public void before() {
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(1234);
        instance.setClusterName(TEST_CLUSTER_NAME);
        instance.setServiceName(TEST_SERVICE_NAME);
        instance.setEnabled(false);
        instances = new ArrayList<>(1);
        instances.add(instance);
        mockmvc = MockMvcBuilders.standaloneSetup(catalogControllerV2).build();
    }
    
    @Test
    void testInstanceList() throws Exception {
        String serviceNameWithoutGroup = NamingUtils.getServiceName(TEST_SERVICE_NAME);
        String groupName = NamingUtils.getGroupName(TEST_SERVICE_NAME);
        when(catalogServiceV2.listAllInstances(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceNameWithoutGroup)).thenReturn(instances);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(
                        UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_CATALOG_CONTEXT + "/instances")
                .param("namespaceId", Constants.DEFAULT_NAMESPACE_ID).param("serviceName", TEST_SERVICE_NAME).param("pageNo", "1")
                .param("pageSize", "100");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        JsonNode data = JacksonUtils.toObj(response.getContentAsString()).get("data").get("instances");
        assertEquals(instances.size(), data.size());
    }
}
