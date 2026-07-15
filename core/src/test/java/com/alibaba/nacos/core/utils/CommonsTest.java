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

package com.alibaba.nacos.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommonsTest {
    
    @Test
    void contextConstants() {
        assertEquals("/nacos", Commons.NACOS_SERVER_CONTEXT);
        assertEquals("/v1", Commons.NACOS_SERVER_VERSION);
        assertEquals("/v2", Commons.NACOS_SERVER_VERSION_V2);
        assertEquals("/v3", Commons.NACOS_SERVER_VERSION_V3);
    }
    
    @Test
    void coreContextConstants() {
        assertNotNull(Commons.DEFAULT_NACOS_CORE_CONTEXT);
        assertEquals("/v1/core", Commons.DEFAULT_NACOS_CORE_CONTEXT);
        assertEquals(Commons.DEFAULT_NACOS_CORE_CONTEXT, Commons.NACOS_CORE_CONTEXT);
        assertEquals("/v2/core", Commons.NACOS_CORE_CONTEXT_V2);
        assertEquals("/v3/admin/core", Commons.NACOS_ADMIN_CORE_CONTEXT_V3);
    }
}
