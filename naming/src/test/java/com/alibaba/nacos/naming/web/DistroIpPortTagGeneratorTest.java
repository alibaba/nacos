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

package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.core.utils.ReuseHttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistroIpPortTagGeneratorTest {

    private final DistroIpPortTagGenerator generator = new DistroIpPortTagGenerator();

    @Test
    void testGetResponsibleTagFromRequestParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/nacos/v3/client/ns/instance");
        request.addParameter("ip", "1.1.1.1");
        request.addParameter("port", "8848");

        assertEquals("1.1.1.1:8848", generator.getResponsibleTag(new ReuseHttpServletRequest(request)));
    }

    @Test
    void testGetResponsibleTagFromFormBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/nacos/v3/client/ns/instance");
        request.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        request.setContent("serviceName=test&ip=1.1.1.1&port=8848".getBytes(StandardCharsets.UTF_8));

        assertEquals("1.1.1.1:8848", generator.getResponsibleTag(new ReuseHttpServletRequest(request)));
    }

    @Test
    void testGetResponsibleTagFromBeatInFormBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/nacos/v1/ns/instance/beat");
        request.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        request.setContent("beat=%7B%22ip%22%3A%221.1.1.1%22%2C%22port%22%3A8848%7D".getBytes(StandardCharsets.UTF_8));

        assertEquals("1.1.1.1:8848", generator.getResponsibleTag(new ReuseHttpServletRequest(request)));
    }
}
