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

package com.alibaba.nacos.ai.service.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test for the {@code default} methods of {@link AiResourcePersistService}.
 *
 * <p>The escaping must stay aligned with {@code ConfigInfoPersistService#generateLikeArgument},
 * because both feed a {@code LIKE} predicate that declares {@code ESCAPE '\'} on the dialects
 * without a default escape character.</p>
 *
 * @author nacos
 */
class AiResourcePersistServiceTest {
    
    private AiResourcePersistService persistService;
    
    @BeforeEach
    void setUp() {
        persistService = Mockito.mock(AiResourcePersistService.class, Mockito.CALLS_REAL_METHODS);
    }
    
    @Test
    void testGenerateLikeArgumentReplacesWildcard() {
        assertEquals("%keyword%", persistService.generateLikeArgument("*keyword*"));
    }
    
    @Test
    void testGenerateLikeArgumentEscapesUnderscore() {
        assertEquals("%nacos\\_test%", persistService.generateLikeArgument("*nacos_test*"));
    }
    
    @Test
    void testGenerateLikeArgumentEscapesUnderscoreWithoutWildcard() {
        assertEquals("nacos\\_test", persistService.generateLikeArgument("nacos_test"));
    }
    
    @Test
    void testGenerateLikeArgumentEscapesBackslash() {
        assertEquals("%C:\\\\path%", persistService.generateLikeArgument("*C:\\path*"));
    }
    
    @Test
    void testGenerateLikeArgumentEscapesTrailingBackslash() {
        assertEquals("%trail\\\\%", persistService.generateLikeArgument("*trail\\*"));
    }
    
    @Test
    void testGenerateLikeArgumentEscapesBackslashBeforeUnderscore() {
        assertEquals("%a\\\\\\_b%", persistService.generateLikeArgument("*a\\_b*"));
    }
}
