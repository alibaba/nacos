/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.naming.pojo.maintainer;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClusterInfoTest {
    
    @Test
    void testValidateWithEmptyClusterName() throws NacosApiException {
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setClusterName("");
        clusterInfo.setHealthChecker(new AbstractHealthChecker.None());
        
        clusterInfo.validate();
        
        assertEquals(Constants.DEFAULT_CLUSTER_NAME, clusterInfo.getClusterName());
    }
    
    @Test
    void testValidateWithNullClusterName() throws NacosApiException {
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setClusterName(null);
        clusterInfo.setHealthChecker(new AbstractHealthChecker.None());
        
        clusterInfo.validate();
        
        assertEquals(Constants.DEFAULT_CLUSTER_NAME, clusterInfo.getClusterName());
    }
    
    @Test
    void testValidateWithValidClusterName() throws NacosApiException {
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setClusterName("test-cluster");
        clusterInfo.setHealthChecker(new AbstractHealthChecker.None());
        
        clusterInfo.validate();
        
        assertEquals("test-cluster", clusterInfo.getClusterName());
    }
}