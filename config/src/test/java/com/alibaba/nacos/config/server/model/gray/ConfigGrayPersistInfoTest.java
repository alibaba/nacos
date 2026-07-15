/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model.gray;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigGrayPersistInfoTest {
    
    @Test
    void testConstructorAndGetters() {
        ConfigGrayPersistInfo info = new ConfigGrayPersistInfo("tag", "1.0.0", "expr1", 10);
        assertEquals("tag", info.getType());
        assertEquals("1.0.0", info.getVersion());
        assertEquals("expr1", info.getExpr());
        assertEquals(10, info.getPriority());
    }
    
    @Test
    void testSetters() {
        ConfigGrayPersistInfo info = new ConfigGrayPersistInfo("tag", "1.0.0", "expr1", 10);
        info.setType("beta");
        info.setVersion("2.0.0");
        info.setExpr("newExpr");
        info.setPriority(99);
        assertEquals("beta", info.getType());
        assertEquals("2.0.0", info.getVersion());
        assertEquals("newExpr", info.getExpr());
        assertEquals(99, info.getPriority());
    }
}
