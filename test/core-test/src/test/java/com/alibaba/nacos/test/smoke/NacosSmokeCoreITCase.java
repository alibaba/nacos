/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.test.smoke;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class NacosSmokeCoreITCase {
    
    private static Logger logger = Logger.getLogger(NacosSmokeCoreITCase.class);
    
    @BeforeEach
    void setUp() {
        logger.info(String.format("nacosSmoke_ITCase: %s;", "setUp"));
    }
    
    @AfterEach
    void tearDown() {
        logger.info(String.format("nacosSmoke_ITCase: %s;", "tearDown"));
    }
    
    @Test
    void testSmoke() {
    }
}
