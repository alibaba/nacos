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

package com.alibaba.nacos.naming.controllers.v3;

import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.ClusterOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.model.form.UpdateClusterForm;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ClusterControllerV3Test.
 *
 * @author Nacos
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClusterControllerV3Test extends BaseTest {
    
    @Mock
    private ClusterOperatorV2Impl clusterOperatorV2;
    
    @Mock
    private HttpServletRequest request;
    
    private ClusterControllerV3 clusterControllerV3;
    
    @BeforeEach
    public void before() {
        super.before();
        clusterControllerV3 = new ClusterControllerV3(clusterOperatorV2);
    }
    
    @Test
    void testUpdate() throws Exception {
        UpdateClusterForm updateClusterForm = new UpdateClusterForm();
        updateClusterForm.setNamespaceId("test-namespace");
        updateClusterForm.setClusterName(TEST_CLUSTER_NAME);
        updateClusterForm.setGroupName(TEST_GROUP_NAME);
        updateClusterForm.setServiceName("test-service");
        updateClusterForm.setCheckPort(1);
        updateClusterForm.setUseInstancePort4Check(true);
        updateClusterForm.setHealthChecker("{\"type\":\"HTTP\"}");
        
        assertEquals("ok", clusterControllerV3.update(updateClusterForm).getData());
        verify(clusterOperatorV2).updateClusterMetadata(eq("test-namespace"), eq(TEST_GROUP_NAME), eq("test-service"),
                eq(TEST_CLUSTER_NAME), any(ClusterMetadata.class));
    }
    
    private void mockRequestParameter(String paramKey, String value) {
        when(request.getParameter(paramKey)).thenReturn(value);
    }
}
