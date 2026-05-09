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

package com.alibaba.nacos.ai.form.pipeline;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineListFormTest {
    
    @Test
    void testBlankResourceTypeThrowsInvalidParam() {
        List<String> blanks = Arrays.asList("", " ", "  ", "\t", "\n", " \t\n ");
        for (String blank : blanks) {
            PipelineListForm form = new PipelineListForm();
            form.setResourceType(blank);
            NacosApiException ex = assertThrows(NacosApiException.class, form::validate);
            assertEquals(400, ex.getErrCode());
        }
    }
    
    @Test
    void testNonBlankResourceTypeWithMissingOptionalsPassesValidation() {
        List<String> resourceTypes = Arrays.asList("SKILL", "AGENTSPEC", "PIPELINE", "type-1");
        for (String resourceType : resourceTypes) {
            PipelineListForm form = new PipelineListForm();
            form.setResourceType(resourceType);
            assertDoesNotThrow(form::validate);
        }
    }
}
