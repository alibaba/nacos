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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class HttpRequestInstanceBuilderTest {
    
    private static final String SERVICE = "service";
    
    private static final String IP = "127.0.0.1";
    
    private static final String PORT = "8848";
    
    @Mock
    private HttpServletRequest request;

    @Mock
    MockEnvironment env;
    
    private HttpRequestInstanceBuilder builder;
    
    @BeforeClass
    public static void setUpBeforeClass() {
        NacosServiceLoader.load(InstanceExtensionHandler.class);
    }
    
    @Before
    public void setUp() throws Exception {
        builder = HttpRequestInstanceBuilder.newBuilder();
        when(request.getParameter(CommonParams.SERVICE_NAME)).thenReturn("service");
        when(request.getParameter("ip")).thenReturn(IP);
        when(request.getParameter("port")).thenReturn(PORT);
        builder.setDefaultInstanceEphemeral(true);
    }
    
    @Test
    public void testBuildSimple() throws NacosException {
        Instance actual = builder.setRequest(request).build();
        assertThat(actual.getServiceName(), is(SERVICE));
        assertThat(actual.getIp(), is(IP));
        assertThat(actual.getPort(), is(Integer.parseInt(PORT)));
        assertThat(actual.getClusterName(), is(UtilsAndCommons.DEFAULT_CLUSTER_NAME));
        assertThat(actual.getWeight(), is(1.0));
        assertTrue(actual.isEphemeral());
        assertTrue(actual.isEnabled());
        assertTrue(actual.isHealthy());
        assertThat(actual.getInstanceId(),
                is(IP + "#" + PORT + "#" + UtilsAndCommons.DEFAULT_CLUSTER_NAME + "#" + SERVICE));
        assertThat(actual.getMetadata().size(), is(1));
        assertThat(actual.getMetadata().get("mock"), is("mock"));
        verify(request).getParameter("mock");
    }
    
    @Test
    public void testBuildFull() throws NacosException {
        when(request.getParameter("weight")).thenReturn("2");
        when(request.getParameter("healthy")).thenReturn("false");
        when(request.getParameter("enabled")).thenReturn("false");
        when(request.getParameter("ephemeral")).thenReturn("false");
        when(request.getParameter("metadata")).thenReturn("{\"a\":\"b\"}");
        when(request.getParameter(CommonParams.CLUSTER_NAME)).thenReturn("cluster");
        Instance actual = builder.setRequest(request).build();
        assertThat(actual.getServiceName(), is(SERVICE));
        assertThat(actual.getIp(), is(IP));
        assertThat(actual.getPort(), is(Integer.parseInt(PORT)));
        assertThat(actual.getClusterName(), is("cluster"));
        assertThat(actual.getWeight(), is(2.0));
        assertFalse(actual.isEphemeral());
        assertFalse(actual.isEnabled());
        assertFalse(actual.isHealthy());
        assertThat(actual.getInstanceId(), is(IP + "#" + PORT + "#" + "cluster" + "#" + SERVICE));
        assertThat(actual.getMetadata().size(), is(2));
        assertThat(actual.getMetadata().get("mock"), is("mock"));
        assertThat(actual.getMetadata().get("a"), is("b"));
        verify(request).getParameter("mock");
    }
    
    @Test(expected = NacosException.class)
    public void testBuildWithIllegalWeight() throws NacosException {
        when(request.getParameter("weight")).thenReturn("10001");
        Instance actual = builder.setRequest(request).build();
    }

    @Test
    public void testBuildWithMetadata() throws NacosException {
        EnvUtil.setEnvironment(env);
        when(env.getProperty(anyString(), eq(Integer.class), anyInt())).thenReturn(-1);
        when(request.getParameter(CommonParams.CLUSTER_NAME)).thenReturn("cluster");
        when(request.getParameter("metadata")).thenReturn("{\n"
                + "        \"preserved.register.source\": \"SPRING_CLOUD\",\n"
                + "        \"preserved.instance.id.generator\": \"snowflake\"\n"
                + "      }");
        Instance instance = builder.setRequest(request).build();
        Assert.isTrue(instance.getInstanceId().endsWith("#" + PORT + "#" + "cluster" + "#" + SERVICE), "instanceId error!");
    }
}
