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

import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.HealthOperatorV2Impl;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.UpdateHealthForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
class HealthControllerV2Test extends BaseTest {
    
    @Mock
    private HealthOperatorV2Impl healthOperatorV2;
    
    @InjectMocks
    private HealthControllerV2 healthControllerV2;
    
    private MockMvc mockmvc;
    
    private UpdateHealthForm updateHealthForm;
    
    @BeforeEach
    public void before() {
        ReflectionTestUtils.setField(healthControllerV2, "healthOperatorV2", healthOperatorV2);
        mockmvc = MockMvcBuilders.standaloneSetup(healthControllerV2).build();
        updateHealthForm = new UpdateHealthForm();
        updateHealthForm.setHealthy(true);
        updateHealthForm.setNamespaceId(TEST_NAMESPACE);
        updateHealthForm.setClusterName(TEST_CLUSTER_NAME);
        updateHealthForm.setGroupName(TEST_GROUP_NAME);
        updateHealthForm.setServiceName(TEST_SERVICE_NAME);
        updateHealthForm.setIp("123.123.123.123");
        updateHealthForm.setPort(8888);
    }
    
    @Test
    void testUpdate() throws Exception {
        doNothing().when(healthOperatorV2).updateHealthStatusForPersistentInstance(TEST_NAMESPACE,
                NamingUtils.getGroupedName(updateHealthForm.getServiceName(), updateHealthForm.getGroupName()), TEST_CLUSTER_NAME,
                "123.123.123.123", 8888, true);
        MockHttpServletRequestBuilder builder = convert(updateHealthForm,
                MockMvcRequestBuilders.put(UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_HEALTH_CONTEXT));
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("ok", JacksonUtils.toObj(response.getContentAsString()).get("data").asText());
    }
    
}
