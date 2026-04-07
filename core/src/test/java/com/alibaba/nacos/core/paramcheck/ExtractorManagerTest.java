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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.impl.ConfigRequestParamExtractor;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link ExtractorManager} unit test.
 */
class ExtractorManagerTest {

    @ExtractorManager.Extractor
    static class DefaultExtractorController {
    }

    @Test
    void getRpcExtractorWithDefaultReturnsDefaultGrpcExtractor() {
        ExtractorManager.Extractor extractor = DefaultExtractorController.class.getAnnotation(ExtractorManager.Extractor.class);
        assertNotNull(extractor);
        AbstractRpcParamExtractor rpc = ExtractorManager.getRpcExtractor(extractor);
        assertNotNull(rpc);
        assertEquals(ExtractorManager.DefaultGrpcExtractor.class, rpc.getClass());
    }

    @Test
    void defaultGrpcExtractorExtractParamReturnsEmptyList() throws Exception {
        AbstractRpcParamExtractor extractor = new ExtractorManager.DefaultGrpcExtractor();
        Request request = mock(Request.class);
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    void getHttpExtractorWithDefaultReturnsDefaultHttpExtractor() {
        ExtractorManager.Extractor extractor = DefaultExtractorController.class.getAnnotation(ExtractorManager.Extractor.class);
        AbstractHttpParamExtractor http = ExtractorManager.getHttpExtractor(extractor);
        assertNotNull(http);
        assertEquals(ExtractorManager.DefaultHttpExtractor.class, http.getClass());
    }

    @Test
    void defaultHttpExtractorExtractParamReturnsEmptyList() throws Exception {
        AbstractHttpParamExtractor extractor = new ExtractorManager.DefaultHttpExtractor();
        HttpServletRequest request = mock(HttpServletRequest.class);
        List<ParamInfo> list = extractor.extractParam(request);
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    void getRpcExtractorWithConfigRequestParamExtractorReturnsExtractor() {
        ExtractorManager.Extractor extractor = ParamExtractorTest.Controller.class.getAnnotation(ExtractorManager.Extractor.class);
        assertNotNull(extractor);
        AbstractRpcParamExtractor rpc = ExtractorManager.getRpcExtractor(extractor);
        assertNotNull(rpc);
        assertEquals(ConfigRequestParamExtractor.class.getSimpleName(), rpc.getClass().getSimpleName());
    }
}
