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

package com.alibaba.nacos.plugin.datasource.enums;

/**
 * The TrustedSqlFunctionEnum enum class is used to enumerate and manage a list of trusted built-in SQL functions.
 * By using this enum, you can verify whether a given SQL function is part of the trusted functions list
 * to avoid potential SQL injection risks.
 *
 * @author blake.qiu
 */
public enum TrustedSqlFunctionEnum {

    /**
     * CURRENT_TIMESTAMP.
     */
    CURRENT_TIMESTAMP("CURRENT_TIMESTAMP"),

    /**
     * NOW().
     */
    NOW("NOW()");

    private final String function;

    TrustedSqlFunctionEnum(String function) {
        this.function = function;
    }

    public String getFunction() {
        return function;
    }
}
