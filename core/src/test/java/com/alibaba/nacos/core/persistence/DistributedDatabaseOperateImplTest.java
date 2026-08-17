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

package com.alibaba.nacos.core.persistence;

import com.alibaba.nacos.persistence.repository.RowMapperManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DistributedDatabaseOperateImplTest {

    @Test
    void testGetBasicResultType() {
        assertSame(Integer.class,
                DistributedDatabaseOperateImpl.getBasicResultType(Integer.class.getCanonicalName()));
        assertSame(Long.class, DistributedDatabaseOperateImpl.getBasicResultType(Long.class.getCanonicalName()));
        assertSame(String.class, DistributedDatabaseOperateImpl.getBasicResultType(String.class.getCanonicalName()));
    }

    @Test
    void testRejectUnsupportedBasicResultType() {
        assertThrows(IllegalArgumentException.class,
                () -> DistributedDatabaseOperateImpl.getBasicResultType(Runtime.class.getCanonicalName()));
        assertThrows(IllegalArgumentException.class,
                () -> DistributedDatabaseOperateImpl.getBasicResultType(null));
    }

    @Test
    void testGetRegisteredRowMapper() {
        assertSame(RowMapperManager.MAP_ROW_MAPPER, DistributedDatabaseOperateImpl.getRequiredRowMapper(
                RowMapperManager.MAP_ROW_MAPPER.getClass().getCanonicalName()));
    }

    @Test
    void testRejectUnregisteredRowMapper() {
        assertThrows(IllegalArgumentException.class,
                () -> DistributedDatabaseOperateImpl.getRequiredRowMapper("com.alibaba.nacos.UnknownRowMapper"));
        assertThrows(IllegalArgumentException.class,
                () -> DistributedDatabaseOperateImpl.getRequiredRowMapper(null));
    }
}
