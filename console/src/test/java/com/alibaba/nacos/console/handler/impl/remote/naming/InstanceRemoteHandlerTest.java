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

package com.alibaba.nacos.console.handler.impl.remote.naming;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.console.handler.impl.remote.AbstractRemoteHandlerTest;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstanceRemoteHandlerTest extends AbstractRemoteHandlerTest {
    
    InstanceRemoteHandler instanceRemoteHandler;
    
    @BeforeEach
    void setUp() {
        super.setUpWithNaming();
        instanceRemoteHandler = new InstanceRemoteHandler(clientHolder);
    }
    
    @Test
    void listInstances() throws NacosException {
        when(namingMaintainerService.listInstances("namespaceId", "groupName", "serviceName", "clusterName",
                false)).thenReturn(Collections.singletonList(new Instance()));
        Page<? extends Instance> page = instanceRemoteHandler.listInstances("namespaceId", "serviceName", "groupName",
                "clusterName", 1, 10);
        assertEquals(1, page.getPageItems().size());
    }
    
    @Test
    void updateInstance() throws NacosException {
        InstanceForm instanceForm = new InstanceForm();
        instanceForm.setServiceName("test");
        instanceForm.setIp("127.0.0.1");
        instanceForm.setPort(3306);
        instanceForm.validate();
        Instance instance = new Instance();
        instanceRemoteHandler.updateInstance(instanceForm, instance);
        verify(namingMaintainerService).updateInstance(Constants.DEFAULT_NAMESPACE_ID, Constants.DEFAULT_GROUP, "test",
                instance);
    }
}