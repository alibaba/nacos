/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.naming.selector;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceInfoContextTest {
    
    @Test
    void testGetAll() {
        ServiceInfo serviceInfo = ServiceInfo.fromKey("aaa@@bbb@@ccc,ddd");
        serviceInfo.addHost(new Instance());
        ServiceInfoContext context = new ServiceInfoContext(serviceInfo);
        assertEquals(1, context.getInstances().size());
        assertEquals("aaa", context.getGroupName());
        assertEquals("bbb", context.getServiceName());
        assertEquals("ccc,ddd", context.getClusters());
    }
}