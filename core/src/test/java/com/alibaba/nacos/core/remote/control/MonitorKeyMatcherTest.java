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

package com.alibaba.nacos.core.remote.control;

import org.junit.Assert;
import org.junit.Test;

/**
 * ${@link MonitorKeyMatcher} unit tests.
 *
 * @author chenglu
 * @date 2021-06-18 13:11
 */
public class MonitorKeyMatcherTest {
    
    @Test
    public void testWithMatchType() {
        boolean match1 = MonitorKeyMatcher.matchWithType("A:*", "A:ddd");
        Assert.assertTrue(match1);
        
        boolean match2 = MonitorKeyMatcher.matchWithType("A:*a", "A:dda");
        Assert.assertTrue(match2);
    
        boolean match3 = MonitorKeyMatcher.matchWithType("A:a*", "A:add");
        Assert.assertTrue(match3);
    
        boolean match4 = MonitorKeyMatcher.matchWithType("A:add", "A:add");
        Assert.assertTrue(match4);
    }
}
