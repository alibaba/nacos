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

package com.alibaba.nacos.ai.form.pipeline;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link PipelineDetailForm} validation.
 */
class PipelineDetailFormValidationTest {
    
    @Test
    void validateRejectsBlankPipelineId() {
        PipelineDetailForm form = new PipelineDetailForm();
        form.setPipelineId(" ");
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void validateAcceptsNonBlankPipelineId() {
        PipelineDetailForm form = new PipelineDetailForm();
        form.setPipelineId("exec-uuid");
        assertDoesNotThrow(form::validate);
    }
}
