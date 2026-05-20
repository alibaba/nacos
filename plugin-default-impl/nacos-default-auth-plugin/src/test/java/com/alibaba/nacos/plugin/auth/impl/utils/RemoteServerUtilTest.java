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

package com.alibaba.nacos.plugin.auth.impl.utils;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteServerUtilTest {
    
    @Test
    void testServerAddressRoundRobinAndContextPath() throws Exception {
        setStaticField("serverAddresses", Arrays.asList("127.0.0.1:8848", "127.0.0.2:8848"));
        setStaticField("index", new AtomicInteger());
        setStaticField("remoteServerContextPath", "/console");
        
        List<String> addresses = RemoteServerUtil.getServerAddresses();
        addresses.clear();
        
        assertEquals(2, RemoteServerUtil.getServerAddresses().size());
        assertEquals("127.0.0.1:8848", RemoteServerUtil.getOneNacosServerAddress());
        assertEquals("127.0.0.2:8848", RemoteServerUtil.getOneNacosServerAddress());
        assertEquals("/console", RemoteServerUtil.getRemoteServerContextPath());
    }
    
    @Test
    void testSingleCheckResult() throws NacosException {
        RemoteServerUtil.singleCheckResult(new HttpRestResult<>(Header.newInstance(), 200, "ok",
            "success"));
        
        NacosException exception = assertThrows(NacosException.class,
            () -> RemoteServerUtil.singleCheckResult(
                new HttpRestResult<>(Header.newInstance(), 500, "bad", "failed")));
        
        assertEquals(500, exception.getErrCode());
        assertEquals("failed", exception.getErrMsg());
    }
    
    @Test
    void testBuildServerRemoteHeader() {
        AuthConfigs authConfigs = mock(AuthConfigs.class);
        when(authConfigs.getServerIdentityKey()).thenReturn("identity");
        when(authConfigs.getServerIdentityValue()).thenReturn("value");
        
        Header header = RemoteServerUtil.buildServerRemoteHeader(authConfigs);
        
        assertEquals("value", header.getValue("identity"));
    }
    
    @Test
    void testBuildServerRemoteHeaderSkipsBlankKey() {
        AuthConfigs authConfigs = mock(AuthConfigs.class);
        when(authConfigs.getServerIdentityKey()).thenReturn("");
        
        Header header = RemoteServerUtil.buildServerRemoteHeader(authConfigs);
        
        assertTrue(header.getHeader().containsKey("Content-Type"));
    }
    
    private static void setStaticField(String fieldName, Object value) throws Exception {
        Field field = RemoteServerUtil.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
