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

package com.alibaba.nacos.test.utils;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.client.naming.beat.BeatReactor;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;

import java.lang.reflect.Field;

public class NamingTestUtils {
    
    public static BeatReactor getBeatReactorByReflection(NamingService namingService)
            throws NoSuchFieldException, IllegalAccessException {
        Field clientProxyField = namingService.getClass().getDeclaredField("clientProxy");
        clientProxyField.setAccessible(true);
        NamingClientProxy namingClientProxy = (NamingClientProxy) clientProxyField.get(namingService);
        Field httpClientProxyField = namingClientProxy.getClass().getDeclaredField("httpClientProxy");
        httpClientProxyField.setAccessible(true);
        NamingHttpClientProxy httpClientProxy = (NamingHttpClientProxy) httpClientProxyField.get(namingClientProxy);
        return httpClientProxy.getBeatReactor();
    }
}
