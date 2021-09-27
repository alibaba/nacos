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

package com.alibaba.nacos.api.naming.pojo.healthcheck;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

public class TestChecker extends AbstractHealthChecker {
    
    @JsonTypeInfo(use = Id.NAME, property = "type")
    public static final String TYPE = "TEST";
    
    private static final long serialVersionUID = 2472091207760970225L;
    
    private String testValue;
    
    public String getTestValue() {
        return testValue;
    }
    
    public void setTestValue(String testValue) {
        this.testValue = testValue;
    }
    
    public TestChecker() {
        super(TYPE);
    }
    
    @Override
    public AbstractHealthChecker clone() throws CloneNotSupportedException {
        return null;
    }
}
