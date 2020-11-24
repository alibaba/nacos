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

package com.alibaba.nacos.test.core.code;

import static org.junit.Assert.assertEquals;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.sys.env.EnvUtil;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.tomcat.util.buf.MessageBytes;
import org.junit.Before;
import org.junit.Test;

/**
 * @author horizonzy
 * @since 1.3.2
 */
public class ControllerMethodsCacheTest {

    private ControllerMethodsCache methodsCache;

    @Before
    public void setUp() throws Exception {
        methodsCache = new ControllerMethodsCache();
        EnvUtil.setContextPath("/nacos");
        methodsCache.initClassMethod("com.alibaba.nacos.config.server.controller");
    }

    @Test
    public void testGetMethod() {
        Request getConfigRequest = buildGetConfigRequest();
        Method getConfigMethod = methodsCache.getMethod(getConfigRequest);
        assertEquals("getConfig", getConfigMethod.getName());

        Request searchConfigRequest = buildSearchConfigRequest();
        Method searchConfigMethod = methodsCache.getMethod(searchConfigRequest);
        assertEquals("searchConfig", searchConfigMethod.getName());

        Request detailConfigInfoRequest = buildDetailConfigInfoRequest();
        Method detailConfigInfoMethod = methodsCache.getMethod(detailConfigInfoRequest);
        assertEquals("detailConfigInfo", detailConfigInfoMethod.getName());
    }

    private Request buildDetailConfigInfoRequest() {
        Map<String, String> parameter = new HashMap<>();
        parameter.put("show", "all");
        return buildRequest("GET", "/nacos/v1/cs/configs", parameter);
    }

    private Request buildSearchConfigRequest() {
        Map<String, String> parameter = new HashMap<>();
        parameter.put("search", "accurate");
        return buildRequest("GET", "/nacos/v1/cs/configs", parameter);
    }

    private Request buildGetConfigRequest() {
        Map<String, String> parameter = new HashMap<>();
        return buildRequest("GET", "/nacos/v1/cs/configs", parameter);
    }

    private Request buildRequest(String method, String path, Map<String, String> parameters) {
        Connector connector = new Connector();
        connector.setParseBodyMethods("GET,POST,PUT,DELETE,PATCH");
        Request request = new Request(connector);
        org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
        MessageBytes messageBytes = coyoteRequest.requestURI();
        messageBytes.setString(path);
        request.setCoyoteRequest(coyoteRequest);
        coyoteRequest.method().setString(method);
        if (parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                coyoteRequest.getParameters().addParameter(entry.getKey(), entry.getValue());
            }
        }
        return request;
    }

}
