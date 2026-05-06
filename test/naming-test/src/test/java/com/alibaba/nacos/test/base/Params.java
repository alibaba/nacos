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

package com.alibaba.nacos.test.base;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Helper class for building HTTP request parameters using Spring's MultiValueMap. Provides methods to construct and
 * append parameters.
 */
public class Params {
    
    private MultiValueMap<String, String> paramMap;
    
    /**
     * Private constructor to enforce usage of static factory method `newParams()`.
     */
    private Params() {
        this.paramMap = new LinkedMultiValueMap<>();
    }
    
    /**
     * Static factory method to create a new instance of Params.
     *
     * @return A new Params instance.
     */
    public static Params newParams() {
        return new Params();
    }
    
    /**
     * Appends a parameter with the specified name and value to the parameter map.
     *
     * @param name  The parameter name.
     * @param value The parameter value.
     * @return This Params instance for method chaining.
     */
    public Params appendParam(String name, String value) {
        this.paramMap.add(name, value);
        return this;
    }
    
    /**
     * Retrieves the constructed parameter map.
     *
     * @return The MultiValueMap containing the appended parameters.
     */
    public MultiValueMap<String, String> done() {
        return paramMap;
    }
}
