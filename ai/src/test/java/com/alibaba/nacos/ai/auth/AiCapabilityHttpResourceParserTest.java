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

package com.alibaba.nacos.ai.auth;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AiCapabilityHttpResourceParserTest {
    
    @Test
    @Secured(signType = SignType.AI, tags = Constants.Tag.ONLY_IDENTITY)
    void shouldIgnoreAllRequestParametersAndHeaders() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Secured secured = getClass().getDeclaredMethod("shouldIgnoreAllRequestParametersAndHeaders")
            .getAnnotation(Secured.class);
        Resource resource = new AiCapabilityHttpResourceParser().parse(request, secured);
        assertEquals("", resource.getNamespaceId());
        assertEquals("", resource.getGroup());
        assertEquals("", resource.getName());
        assertEquals(SignType.AI, resource.getType());
        assertEquals("r", resource.getProperties().getProperty(Constants.Resource.ACTION));
        assertEquals(Constants.Tag.ONLY_IDENTITY,
            resource.getProperties().getProperty(Constants.Tag.ONLY_IDENTITY));
        assertNull(resource.getProperties().getProperty(Constants.Tag.ALLOW_ANONYMOUS));
        verifyNoInteractions(request);
    }
}
