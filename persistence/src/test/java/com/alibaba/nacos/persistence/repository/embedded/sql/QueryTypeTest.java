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
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.alibaba.nacos.persistence.repository.embedded.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryTypeTest {
    
    @Test
    void testQueryTypeConstants() {
        assertEquals(0, QueryType.QUERY_ONE_WITH_MAPPER_WITH_ARGS);
        assertEquals(1, QueryType.QUERY_ONE_NO_MAPPER_NO_ARGS);
        assertEquals(2, QueryType.QUERY_ONE_NO_MAPPER_WITH_ARGS);
        assertEquals(3, QueryType.QUERY_MANY_WITH_MAPPER_WITH_ARGS);
        assertEquals(4, QueryType.QUERY_MANY_WITH_LIST_WITH_ARGS);
        assertEquals(5, QueryType.QUERY_MANY_NO_MAPPER_WITH_ARGS);
    }
}
