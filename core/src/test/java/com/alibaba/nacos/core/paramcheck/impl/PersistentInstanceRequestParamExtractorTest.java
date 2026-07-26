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
import com.alibaba.nacos.api.naming.remote.request.PersistentInstanceRequest;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link PersistentInstanceRequestParamExtractor} unit test.
 */
class PersistentInstanceRequestParamExtractorTest {
    
    private PersistentInstanceRequestParamExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new PersistentInstanceRequestParamExtractor();
    }
    
    @Test
    void extractParamWithNullInstance() {
        PersistentInstanceRequest request = new PersistentInstanceRequest();
        request.setNamespace("ns1");
        request.setServiceName("svc1");
        request.setGroupName("g1");
        request.setInstance(null);
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("ns1", list.get(0).getNamespaceId());
        assertNull(list.get(0).getIp());
    }
    
    @Test
    void extractParamWithInstance() {
        PersistentInstanceRequest request = new PersistentInstanceRequest();
        request.setNamespace("ns1");
        request.setServiceName("svc1");
        request.setGroupName("g1");
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8848);
        instance.setClusterName("cluster1");
        instance.setMetadata(Collections.singletonMap("k", "v"));
        request.setInstance(instance);
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("127.0.0.1", list.get(0).getIp());
        assertEquals("8848", list.get(0).getPort());
    }
}
