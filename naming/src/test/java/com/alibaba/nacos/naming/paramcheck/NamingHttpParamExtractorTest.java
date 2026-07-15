/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.paramcheck;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NamingHttpParamExtractorTest {
    
    @Test
    void testDefaultExtractorUsesAliasesAndSplitGroupServiceName() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("namespaceId", "namespace");
        request.addParameter("serviceNameParam", "group@@service");
        request.addParameter("groupNameParam", "fallbackGroup");
        request.addParameter("ip", "1.1.1.1");
        request.addParameter("checkPort", "8848");
        request.addParameter("cluster", "clusterA");
        request.addParameter("metadata", "env=dev,zone=hangzhou");
        
        List<ParamInfo> actual = new NamingDefaultHttpParamExtractor().extractParam(request);
        
        assertEquals(1, actual.size());
        ParamInfo paramInfo = actual.get(0);
        assertEquals("namespace", paramInfo.getNamespaceId());
        assertEquals("group", paramInfo.getGroup());
        assertEquals("service", paramInfo.getServiceName());
        assertEquals("1.1.1.1", paramInfo.getIp());
        assertEquals("8848", paramInfo.getPort());
        assertEquals("clusterA", paramInfo.getCluster());
        assertEquals("dev", paramInfo.getMetadata().get("env"));
        assertEquals("hangzhou", paramInfo.getMetadata().get("zone"));
    }
    
    @Test
    void testDefaultExtractorUsesPrimaryParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("namespaceId", "namespace");
        request.addParameter("serviceName", "service");
        request.addParameter("groupName", "group");
        request.addParameter("port", "8848");
        request.addParameter("clusterName", "clusterA");
        
        ParamInfo actual = new NamingDefaultHttpParamExtractor().extractParam(request).get(0);
        
        assertEquals("namespace", actual.getNamespaceId());
        assertEquals("group", actual.getGroup());
        assertEquals("service", actual.getServiceName());
        assertEquals("8848", actual.getPort());
        assertEquals("clusterA", actual.getCluster());
    }
    
    @Test
    void testInstanceListExtractorSplitsGroupServiceName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("namespaceId", "namespace");
        request.addParameter("serviceName", "group@@service");
        request.addParameter("groupName", "ignoredGroup");
        request.addParameter("clusters", "clusterA,clusterB");
        
        ParamInfo actual = new NamingInstanceListHttpParamExtractor().extractParam(request).get(0);
        
        assertEquals("namespace", actual.getNamespaceId());
        assertEquals("group", actual.getGroup());
        assertEquals("service", actual.getServiceName());
        assertEquals("clusterA,clusterB", actual.getClusters());
    }
    
    @Test
    void testInstanceBeatExtractorAddsBeatParamInfo() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("namespaceId", "namespace");
        request.addParameter("serviceName", "group@@service");
        request.addParameter("ip", "1.1.1.1");
        request.addParameter("port", "8848");
        request.addParameter("beat", JacksonUtils.toJson(createBeat()));
        
        List<ParamInfo> actual = new NamingInstanceBeatHttpParamExtractor().extractParam(request);
        
        assertEquals(2, actual.size());
        assertEquals("group", actual.get(0).getGroup());
        assertEquals("service", actual.get(0).getServiceName());
        assertEquals("1.1.1.1", actual.get(0).getIp());
        assertEquals("8848", actual.get(0).getPort());
        assertEquals("2.2.2.2", actual.get(1).getIp());
        assertEquals("9848", actual.get(1).getPort());
        assertEquals("beatCluster", actual.get(1).getCluster());
    }
    
    @Test
    void testInstanceMetadataBatchExtractorAddsInstanceParamInfos() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("namespaceId", "namespace");
        request.addParameter("serviceName", "group@@service");
        request.addParameter("metadata", "version=1.0");
        request.addParameter("instances",
            JacksonUtils.toJson(Collections.singletonList(createInstance())));
        
        List<ParamInfo> actual =
            new NamingInstanceMetadataBatchHttpParamExtractor().extractParam(request);
        
        assertEquals(2, actual.size());
        assertEquals("namespace", actual.get(0).getNamespaceId());
        assertEquals("group", actual.get(0).getGroup());
        assertEquals("service", actual.get(0).getServiceName());
        assertEquals("1.0", actual.get(0).getMetadata().get("version"));
        assertEquals("3.3.3.3", actual.get(1).getIp());
        assertEquals("10848", actual.get(1).getPort());
        assertEquals("instanceCluster", actual.get(1).getCluster());
    }
    
    private RsInfo createBeat() {
        RsInfo beat = new RsInfo();
        beat.setIp("2.2.2.2");
        beat.setPort(9848);
        beat.setCluster("beatCluster");
        return beat;
    }
    
    private Instance createInstance() {
        Instance instance = new Instance();
        instance.setIp("3.3.3.3");
        instance.setPort(10848);
        instance.setClusterName("instanceCluster");
        return instance;
    }
}
