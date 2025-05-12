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

package com.alibaba.nacos.console.handler.impl.remote.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.handler.impl.remote.AbstractRemoteHandlerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ClusterRemoteHandlerTest extends AbstractRemoteHandlerTest {
    
    ClusterRemoteHandler clusterRemoteHandler;
    
    @BeforeEach
    void setUp() {
        super.setUpWithNaming();
        clusterRemoteHandler = new ClusterRemoteHandler(clientHolder);
    }
    
    @Test
    void getNodeList() throws NacosException {
        Collection<NacosMember> mockList = new LinkedList<>();
        mockList.add(new NacosMember());
        when(namingMaintainerService.listClusterNodes(StringUtils.EMPTY, StringUtils.EMPTY)).thenReturn(mockList);
        Collection<? extends NacosMember> actual = clusterRemoteHandler.getNodeList(StringUtils.EMPTY);
        assertEquals(mockList, actual);
    }
}