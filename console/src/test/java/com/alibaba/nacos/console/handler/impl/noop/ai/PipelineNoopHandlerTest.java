/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Property 8: NoopHandler always returns 501.
 *
 * <p>Any call to getPipeline or listPipelines should throw HTTP 501
 * with error code API_FUNCTION_DISABLED.</p>
 *
 * <p><b>Validates: Requirement 5.3</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineNoopHandlerTest {
    
    private PipelineNoopHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new PipelineNoopHandler();
    }
    
    @Test
    void getPipelineShouldThrow501() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.getPipeline("any-id"));
        assertEquals(501, ex.getErrCode());
        assertEquals(ErrorCode.API_FUNCTION_DISABLED.getCode(), ex.getDetailErrCode());
    }
    
    @Test
    void listPipelinesShouldThrow501() {
        NacosApiException ex = assertThrows(NacosApiException.class,
            () -> handler.listPipelines("type", "name", "ns", "v1", 1, 10));
        assertEquals(501, ex.getErrCode());
        assertEquals(ErrorCode.API_FUNCTION_DISABLED.getCode(), ex.getDetailErrCode());
    }
}
