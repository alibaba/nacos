/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.metadata;

import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.utils.Constants;
import com.google.protobuf.ByteString;
import org.junit.Test;

import java.util.Map;

public class NamingMetadataManagerTest {
    
    @Test
    public void testSerialize() {
        NamingMetadataManager namingMetadataManager = new NamingMetadataManager();
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.setProtectThreshold(0.5f);
        metadata.getExtendData().put("aaa", "111");
        metadata.getExtendData().put("test", "t");
        namingMetadataManager.updateServiceMetadata(Service.newService("t", "g", "b"), metadata);
        byte[] sp = SerializeFactory.getDefault().serialize(namingMetadataManager.getServiceMetadataSnapshot());
        Map<Service, ServiceMetadata> actual = SerializeFactory.getDefault().deserialize(sp);
        System.out.println(actual);
    }
    
    @Test
    public void testSerialize2() {
        MetadataOperation<ServiceMetadata> operation = new MetadataOperation<>();
        operation.setNamespace("n");
        operation.setGroup("g");
        operation.setServiceName("s");
        ServiceMetadata metadata = new ServiceMetadata();
        metadata.setProtectThreshold(0.5f);
        metadata.getExtendData().put("aaa", "111");
        metadata.getExtendData().put("test", "t");
        operation.setMetadata(metadata);
        byte[] sp = SerializeFactory.getDefault().serialize(operation);
        MetadataOperation<ServiceMetadata> actual = SerializeFactory.getDefault().deserialize(sp);
        System.out.println(actual);
    }
}
