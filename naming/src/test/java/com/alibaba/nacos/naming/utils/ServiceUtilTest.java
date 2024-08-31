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

package com.alibaba.nacos.naming.utils;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServiceUtilTest {
    
    @Test
    void testSelectInstances() {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setGroupName("groupName");
        serviceInfo.setName("serviceName");
        serviceInfo.setChecksum("checkSum");
        serviceInfo.setAllIPs(false);
        ServiceInfo cluster = ServiceUtil.selectInstances(serviceInfo, "cluster");
        assertNotNull(cluster);
    }
}
