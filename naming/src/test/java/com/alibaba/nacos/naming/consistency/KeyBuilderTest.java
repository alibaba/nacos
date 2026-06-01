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

package com.alibaba.nacos.naming.consistency;

import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyBuilderTest {
    
    @Test
    void testBuildKeys() {
        new KeyBuilder();
        
        assertEquals("com.alibaba.nacos.naming.domains.meta.namespace##group@@service",
            KeyBuilder.buildServiceMetaKey("namespace", "group@@service"));
        assertEquals("com.alibaba.nacos.naming.domains.meta." + UtilsAndCommons.SWITCH_DOMAIN_NAME,
            KeyBuilder.getSwitchDomainKey());
        assertTrue(KeyBuilder.matchSwitchKey(KeyBuilder.getSwitchDomainKey()));
        assertFalse(KeyBuilder.matchSwitchKey("other"));
    }
}
