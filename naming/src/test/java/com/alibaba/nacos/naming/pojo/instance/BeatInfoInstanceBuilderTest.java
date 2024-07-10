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

package com.alibaba.nacos.naming.pojo.instance;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BeatInfoInstanceBuilderTest {
    
    @Mock
    private HttpServletRequest request;
    
    private RsInfo beatInfo;
    
    private BeatInfoInstanceBuilder builder;
    
    @BeforeAll
    static void setUpBeforeClass() {
        NacosServiceLoader.load(InstanceExtensionHandler.class);
    }
    
    @BeforeEach
    void setUp() throws Exception {
        builder = BeatInfoInstanceBuilder.newBuilder();
        builder.setRequest(request);
        beatInfo = new RsInfo();
        beatInfo.setServiceName("g@@s");
        beatInfo.setCluster("c");
        beatInfo.setIp("1.1.1.1");
        beatInfo.setPort(8848);
        beatInfo.setWeight(10);
        beatInfo.setMetadata(new HashMap<>());
    }
    
    @Test
    void testBuild() {
        Instance actual = builder.setServiceName("g@@s").setBeatInfo(beatInfo).build();
        assertThat(actual.getServiceName(), is("g@@s"));
        assertThat(actual.getIp(), is("1.1.1.1"));
        assertThat(actual.getPort(), is(8848));
        assertThat(actual.getClusterName(), is("c"));
        assertThat(actual.getWeight(), is(10.0));
        assertTrue(actual.isEphemeral());
        assertTrue(actual.isEnabled());
        assertTrue(actual.isHealthy());
        assertThat(actual.getInstanceId(), is("1.1.1.1#8848#c#g@@s"));
        assertThat(actual.getMetadata().size(), is(1));
        assertThat(actual.getMetadata().get("mock"), is("mock"));
        verify(request).getParameter("mock");
    }
}
