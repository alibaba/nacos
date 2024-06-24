/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link OverrideParameterRequestWrapper} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 14:11
 */
class OverrideParameterRequestWrapperTest {
    
    @Test
    void testOverrideParameterRequestWrapper() {
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.addParameter("test1", "value1");
        OverrideParameterRequestWrapper wrapper = OverrideParameterRequestWrapper.buildRequest(httpServletRequest);
        String value1 = wrapper.getParameter("test1");
        assertEquals("value1", value1);
        
        wrapper.addParameter("test2", "value2");
        assertEquals("value2", wrapper.getParameter("test2"));
    }
}
