/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.remote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConnectionTypeTest {
    
    @Test
    void testGetByType() {
        ConnectionType connectionType = ConnectionType.getByType("GRPC");
        assertNotNull(connectionType);
        assertEquals("GRPC", connectionType.getType());
        assertEquals("Grpc Connection", connectionType.getName());
    }
    
    @Test
    void testGetByNonExistType() {
        ConnectionType connectionType = ConnectionType.getByType("HTTP");
        assertNull(connectionType);
    }
}