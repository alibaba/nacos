/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.code.condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link ParamRequestCondition} unit test.
 *
 * @author chenglu
 * @date 2021-07-06 11:56
 */
class ParamRequestConditionTest {
    
    private ParamRequestCondition paramRequestCondition;
    
    @BeforeEach
    void setUp() {
        paramRequestCondition = new ParamRequestCondition("test=1244");
    }
    
    @Test
    void testGetExpressions() {
        assertEquals(1, paramRequestCondition.getExpressions().size());
    }
    
    @Test
    void testGetMatchingCondition() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ParamRequestCondition paramRequestCondition1 = paramRequestCondition.getMatchingCondition(request);
        assertNull(paramRequestCondition1);
        
        request.setParameter("test", "1244");
        ParamRequestCondition paramRequestCondition2 = paramRequestCondition.getMatchingCondition(request);
        assertNotNull(paramRequestCondition2);
    }
}
