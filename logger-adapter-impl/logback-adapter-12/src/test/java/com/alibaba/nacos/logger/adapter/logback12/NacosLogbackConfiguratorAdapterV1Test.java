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

package com.alibaba.nacos.logger.adapter.logback12;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.Status;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NacosLogbackConfiguratorAdapterV1Test {
    
    @Mock
    private URL url;
    
    @Mock
    private URLConnection urlConnection;
    
    @Mock
    private InputStream inputStream;
    
    ContextBase context;
    
    NacosLogbackConfiguratorAdapterV1 nacosLogbackConfiguratorAdapter;
    
    @Before
    public void setUp() throws Exception {
        nacosLogbackConfiguratorAdapter = new NacosLogbackConfiguratorAdapterV1();
        context = new ContextBase();
        nacosLogbackConfiguratorAdapter.setContext(context);
        when(url.openConnection()).thenReturn(urlConnection);
        when(urlConnection.getInputStream()).thenReturn(inputStream);
    }
    
    @After
    public void tearDown() throws Exception {
        context.stop();
    }
    
    @Test
    public void testConfigureWithError() throws Exception {
        doThrow(new IOException("test")).when(inputStream).close();
        try {
            nacosLogbackConfiguratorAdapter.configure(url);
        } catch (JoranException e) {
            List<Status> statusList = context.getStatusManager().getCopyOfStatusList();
            assertFalse(statusList.isEmpty());
            assertTrue(statusList.get(statusList.size() - 1) instanceof ErrorStatus);
            assertEquals("Could not close input stream", statusList.get(statusList.size() - 1).getMessage());
        }
    }
}