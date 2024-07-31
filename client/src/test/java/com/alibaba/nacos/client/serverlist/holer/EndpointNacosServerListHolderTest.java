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

package com.alibaba.nacos.client.serverlist.holer;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.serverlist.holder.impl.EndpointNacosServerListHolder;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

/**
 * endpoint test.
 *
 * @author xz
 * @since 2024/7/25 15:23
 */
public class EndpointNacosServerListHolderTest {

    @Test
    public void testGetServerList() {
        EndpointNacosServerListHolder holder = new EndpointNacosServerListHolder();
        List<String> serverList = holder.getServerList();

        assertTrue(serverList.isEmpty());
    }

    @Test
    public void testInitServerList() throws Exception {
        final EndpointNacosServerListHolder holder = new EndpointNacosServerListHolder();
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        NacosRestTemplate mock = Mockito.mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("127.0.0.1:8848");
        a.setCode(200);
        Mockito.when(mock.get(any(), any(), any(), any())).thenReturn(a);

        final Field nacosRestTemplate = EndpointNacosServerListHolder.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplate.setAccessible(true);
        nacosRestTemplate.set(holder, mock);
        boolean canApply = holder.canApply(NacosClientProperties.PROTOTYPE.derive(properties));
        assertTrue(canApply);
        List<String> serverList = holder.getServerList();

        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
    }

    @Test
    public void testTestGetName() {
        EndpointNacosServerListHolder holder = new EndpointNacosServerListHolder();

        assertEquals(holder.getName(), "endpoint");
    }
}