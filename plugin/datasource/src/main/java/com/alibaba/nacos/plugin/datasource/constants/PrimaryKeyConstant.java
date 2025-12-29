/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.constants;

/**
 * PrimaryKeyConstant.
 *
 * @author Long Yu
 */
public class PrimaryKeyConstant {

    /**
     * replace lower Statement.RETURN_GENERATED_KEYS into id key.
     */
    public static final String[] LOWER_RETURN_PRIMARY_KEYS = new String[]{"id"};

    /**
     * upper replace Statement.RETURN_GENERATED_KEYS into id key.
     * using dameng database.
     */
    public static final String[] UPPER_RETURN_PRIMARY_KEYS = new String[]{"ID"};

}
