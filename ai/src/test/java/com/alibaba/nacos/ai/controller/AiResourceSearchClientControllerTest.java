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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.form.search.client.AiResourceSearchForm;
import com.alibaba.nacos.ai.service.search.AiResourceSearchApplicationService;
import com.alibaba.nacos.api.ai.model.search.AiResourceSearchResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiResourceSearchClientController}.
 */
class AiResourceSearchClientControllerTest {
    
    @Test
    void searchShouldValidateDefaultsAndDelegate() throws NacosException {
        AiResourceSearchApplicationService service =
            mock(AiResourceSearchApplicationService.class);
        AiResourceSearchClientController controller =
            new AiResourceSearchClientController(service);
        AiResourceSearchForm form = new AiResourceSearchForm();
        AiResourceSearchResponse response = new AiResourceSearchResponse();
        response.setNextCursor("next");
        when(service.search(form)).thenReturn(response);
        
        Result<AiResourceSearchResponse> result = controller.search(form);
        
        assertEquals("public", form.getNamespaceId());
        assertEquals(20, form.getLimit());
        assertEquals("next", result.getData().getNextCursor());
        verify(service).search(form);
    }
}
