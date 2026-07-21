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

package com.alibaba.nacos.airegistry.controller;

import com.alibaba.nacos.airegistry.model.ard.ArdCatalog;
import com.alibaba.nacos.airegistry.service.ard.ArdSearchService;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArdWellKnownController}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ArdWellKnownControllerTest {
    
    @Mock
    private ArdSearchService ardSearchService;
    
    @Test
    void catalogShouldReturnHostCatalog() throws NacosException {
        ArdCatalog catalog = new ArdCatalog();
        when(ardSearchService.hostCatalog()).thenReturn(catalog);
        
        assertSame(catalog, controller().catalog());
    }
    
    @Test
    void catalogShouldUseAiReadAuthentication() throws NoSuchMethodException {
        Method method = ArdWellKnownController.class.getMethod("catalog");
        Secured secured = method.getAnnotation(Secured.class);
        
        assertNotNull(secured);
        assertEquals(ActionTypes.READ, secured.action());
        assertEquals(SignType.AI, secured.signType());
        assertEquals(ApiType.OPEN_API, secured.apiType());
    }
    
    private ArdWellKnownController controller() {
        return new ArdWellKnownController(ardSearchService);
    }
}
