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

import org.junit.Assert;
import org.junit.Test;

import static com.alibaba.nacos.sys.env.Constants.REQUEST_PATH_SEPARATOR;

/**
 * {@link PathRequestCondition} unit test.
 *
 * @author chenglu
 * @date 2021-07-06 16:37
 */
public class PathRequestConditionTest {
    
    @Test
    public void testPathRequestCondition() {
        PathRequestCondition pathRequestCondition = new PathRequestCondition("method" + REQUEST_PATH_SEPARATOR + "path");
        Assert.assertTrue(pathRequestCondition.toString().contains("path"));
    }
}
