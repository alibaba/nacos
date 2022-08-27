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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.config.server.service.repository.PersistService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;

/**
 * NamespaceController unit test.
 * @ClassName: NamespaceControllerTest
 * @Author: ChenHao26
 * @Date: 2022/8/13 09:32
 * @Description: TODO
 */
@RunWith(MockitoJUnitRunner.class)
public class NamespaceControllerTest {
    
    @InjectMocks
    private NamespaceController namespaceController;
    
    @Mock(lenient = true)
    private PersistService persistService;
    
    private MockMvc mockmvc;
    
    private static final String NAMESPACE_URL = "/v1/console/namespaces";
    
    @Before
    public void setUp() {
        mockmvc = MockMvcBuilders.standaloneSetup(namespaceController).build();
    }
    
    @Test
    public void getNamespaces() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(NAMESPACE_URL);
        Assert.assertEquals(200, mockmvc.perform(builder).andReturn().getResponse().getStatus());
    }
    
    @Test
    public void getNamespaceByNamespaceId() throws Exception {
        String url = "/v1/console/namespaces";
        Mockito.when(persistService.findTenantByKp(any(String.class))).thenReturn(null);
        Mockito.when(persistService.configInfoCount(any(String.class))).thenReturn(0);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(url)
                .param("show", "all").param("namespaceId",  "");
        Assert.assertEquals(200, mockmvc.perform(builder).andReturn().getResponse().getStatus());
    }
    
    @Test
    public void createNamespace() throws Exception {
       
        try {
            persistService.insertTenantInfoAtomic(String.valueOf(1), "testId", "name",
                    "testDesc", "resourceId", 3000L);
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(NAMESPACE_URL)
                    .param("customNamespaceId", "nsId")
                    .param("namespaceName", "nsService").param("namespaceDesc", "desc");
            Assert.assertEquals(200, mockmvc.perform(builder).andReturn().getResponse().getStatus());
        } catch (Exception e) {
            Assert.assertNull(e);
        }
    
    }
    
    @Test
    public void checkNamespaceIdExist() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(NAMESPACE_URL)
                .param("checkNamespaceIdExist", "true").param("customNamespaceId", "12");
        MockHttpServletResponse response = mockmvc.perform(builder).andReturn().getResponse();
        Assert.assertEquals("false", response.getContentAsString());
        Mockito.when(persistService.tenantInfoCountByTenantId("")).thenReturn(0);
        Mockito.when(persistService.tenantInfoCountByTenantId("123")).thenReturn(1);
    }
    
    @Test
    public void editNamespace() {
        try {
            persistService.updateTenantNameAtomic("1", "testId", "nsShowName", "namespaceDesc");
            createNamespace();
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(NAMESPACE_URL)
                    .param("namespace", "testId").param("namespaceShowName", "nsShowName")
                    .param("namespaceDesc", "desc");
            Assert.assertEquals("true", mockmvc.perform(builder).andReturn().getResponse().getContentAsString());
        } catch (Exception e) {
            Assert.assertNull(e);
        }
    }
    
    @Test
    public void deleteConfig() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(NAMESPACE_URL)
                .param("namespaceId", "");
        Assert.assertEquals("true", mockmvc.perform(builder).andReturn().getResponse().getContentAsString());
    }
}
