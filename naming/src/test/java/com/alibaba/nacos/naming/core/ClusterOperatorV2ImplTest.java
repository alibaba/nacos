/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ClusterOperatorV2ImplTest extends TestCase {
    
    private ClusterOperatorV2Impl clusterOperatorV2Impl;
    
    @Mock
    private NamingMetadataOperateService metadataOperateServiceMock;
    
    private ClusterMetadata clusterMetadata;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        Service service = Service.newService("namespace_test", "group_test", "name_test");
        ServiceManager.getInstance().getSingleton(service);
        clusterOperatorV2Impl = new ClusterOperatorV2Impl(metadataOperateServiceMock);
        clusterMetadata = new ClusterMetadata();
    }
    
    @Test
    public void testUpdateClusterMetadata() throws NacosException {
        clusterOperatorV2Impl
                .updateClusterMetadata("namespace_test", "group_test@@name_test", "clusterName_test", clusterMetadata);
        verify(metadataOperateServiceMock)
                .addClusterMetadata(any(Service.class), anyString(), any(ClusterMetadata.class));
    }
}