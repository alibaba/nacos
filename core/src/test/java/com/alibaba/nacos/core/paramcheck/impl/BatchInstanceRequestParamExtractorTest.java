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

package com.alibaba.nacos.core.paramcheck.impl;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.remote.request.BatchInstanceRequest;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link BatchInstanceRequestParamExtractor} unit test.
 */
class BatchInstanceRequestParamExtractorTest {
    
    private BatchInstanceRequestParamExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new BatchInstanceRequestParamExtractor();
    }
    
    @Test
    void extractParamWithNullInstances() {
        BatchInstanceRequest request = new BatchInstanceRequest();
        request.setNamespace("ns1");
        request.setServiceName("svc1");
        request.setGroupName("g1");
        request.setInstances(null);
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("ns1", list.get(0).getNamespaceId());
    }
    
    @Test
    void extractParamWithInstances() {
        BatchInstanceRequest request = new BatchInstanceRequest();
        request.setNamespace("ns1");
        request.setServiceName("svc1");
        request.setGroupName("g1");
        Instance i1 = new Instance();
        i1.setIp("127.0.0.1");
        i1.setPort(8848);
        i1.setServiceName("s1");
        i1.setClusterName("c1");
        i1.setMetadata(Collections.singletonMap("k", "v"));
        request.setInstances(Collections.singletonList(i1));
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals("127.0.0.1", list.get(1).getIp());
        assertEquals("8848", list.get(1).getPort());
        assertEquals("s1", list.get(1).getServiceName());
        assertEquals("c1", list.get(1).getCluster());
    }
}
