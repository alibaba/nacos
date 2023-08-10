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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.common.utils.HttpMethod;
import junit.framework.TestCase;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The type Http param extractor manager test.
 */
public class HttpParamExtractorManagerTest extends TestCase {
    
    /**
     * Test get instance.
     */
    public void testGetInstance() {
        HttpParamExtractorManager paramExtractorManager = HttpParamExtractorManager.getInstance();
    }
    
    /**
     * Test get extractor.
     *
     * @throws Exception the exception
     */
    public void testGetExtractor() throws Exception {
        HttpParamExtractorManager paramExtractorManager = HttpParamExtractorManager.getInstance();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/nacos/v1/ns/instance");
        request.setMethod(HttpMethod.POST);
        AbstractHttpParamExtractor extractor = paramExtractorManager.getExtractor(request.getRequestURI(), request.getMethod(), "naming");
        extractor.extractParam(request);
    }
}