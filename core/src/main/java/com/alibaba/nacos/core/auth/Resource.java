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
package com.alibaba.nacos.core.auth;


import com.alibaba.fastjson.JSON;

/**
 * Resource used in authorization.
 *
 * @author nkorange
 * @since 1.2.0
 */
public class Resource {

    public static final String SPLITTER = ":";
    public static final String ANY = "*";

    /**
     * The unique key of resource.
     */
    private String key;

    public Resource(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public String parseName() {
        return key.substring(0, key.lastIndexOf(SPLITTER));
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
