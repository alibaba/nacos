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

package com.alibaba.nacos.persistence.repository.embedded.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectRequestTest {
    
    @Test
    void testBuild() {
        SelectRequest.SelectRequestBuilder builder = SelectRequest.builder();
        builder.queryType(QueryType.QUERY_ONE_WITH_MAPPER_WITH_ARGS);
        builder.sql("SELECT 1");
        builder.args(new Object[] {"1"});
        builder.className(Integer.class.getCanonicalName());
        SelectRequest request = builder.build();
        assertEquals(QueryType.QUERY_ONE_WITH_MAPPER_WITH_ARGS, request.getQueryType());
        assertEquals("SELECT 1", request.getSql());
        assertEquals("java.lang.Integer", request.getClassName());
        assertEquals(1, request.getArgs().length);
        assertEquals("1", request.getArgs()[0]);
        assertEquals("SelectRequest{queryType=0, sql='SELECT 1', args=[1], className='java.lang.Integer'}",
                request.toString());
    }
}