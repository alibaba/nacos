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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosLogbackConfiguratorAdapterV1Test {
    
    ContextBase context;
    
    NacosLogbackConfiguratorAdapterV1 nacosLogbackConfiguratorAdapter;
    
    @Mock
    private URL url;
    
    @Mock
    private URLConnection urlConnection;
    
    @Mock
    private InputStream inputStream;
    
    @BeforeEach
    void setUp() throws Exception {
        nacosLogbackConfiguratorAdapter = new NacosLogbackConfiguratorAdapterV1();
        context = new ContextBase();
        nacosLogbackConfiguratorAdapter.setContext(context);
        when(url.openConnection()).thenReturn(urlConnection);
        when(urlConnection.getInputStream()).thenReturn(inputStream);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        context.stop();
    }
    
    @Test
    void testConfigureWithError() throws Exception {
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