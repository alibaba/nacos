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

package com.alibaba.nacos.common.paramcheck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamCheckerManagerTest {
    
    @Test
    void testGetParamCheckerNonExistType() {
        assertTrue(ParamCheckerManager.getInstance().getParamChecker("non") instanceof DefaultParamChecker);
    }
    
    @Test
    void testGetParamCheckerNull() {
        assertTrue(ParamCheckerManager.getInstance().getParamChecker("") instanceof DefaultParamChecker);
        assertTrue(ParamCheckerManager.getInstance().getParamChecker(null) instanceof DefaultParamChecker);
    }
    
    @Test
    void testGetParamCheckerDefault() {
        assertTrue(ParamCheckerManager.getInstance().getParamChecker("default") instanceof DefaultParamChecker);
    }
    
    @Test
    void testGetParamCheckerOther() {
        assertTrue(ParamCheckerManager.getInstance().getParamChecker("mock") instanceof MockParamChecker);
    }
}