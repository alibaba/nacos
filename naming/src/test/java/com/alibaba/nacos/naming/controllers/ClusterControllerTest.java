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

package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.ClusterOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class ClusterControllerTest extends BaseTest {
    
    @Mock
    private ClusterOperatorV2Impl clusterOperatorV2;
    
    @Mock
    private HttpServletRequest request;
    
    private ClusterController clusterController;
    
    @BeforeEach
    public void before() {
        super.before();
        clusterController = new ClusterController(clusterOperatorV2);
    }
    
    @Test
    void testUpdate() throws Exception {
        mockRequestParameter(CommonParams.NAMESPACE_ID, "test-namespace");
        mockRequestParameter(CommonParams.CLUSTER_NAME, TEST_CLUSTER_NAME);
        mockRequestParameter(CommonParams.SERVICE_NAME, TEST_SERVICE_NAME);
        mockRequestParameter("checkPort", "1");
        mockRequestParameter("useInstancePort4Check", "true");
        mockRequestParameter("healthChecker", "{\"type\":\"HTTP\"}");
        assertEquals("ok", clusterController.update(request));
        verify(clusterOperatorV2).updateClusterMetadata(eq("test-namespace"), eq(TEST_SERVICE_NAME), eq(TEST_CLUSTER_NAME),
                any(ClusterMetadata.class));
    }
    
    private void mockRequestParameter(String paramKey, String value) {
        when(request.getParameter(paramKey)).thenReturn(value);
    }
}
