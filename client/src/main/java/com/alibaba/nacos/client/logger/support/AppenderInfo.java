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
package com.alibaba.nacos.client.logger.support;

import java.util.HashMap;

/**
 * @author zhuyong on 2017/6/30.
 */
public class AppenderInfo extends HashMap {

    private static String name = "name";
    private static String type = "type";
    private static String file = "file";

    public String getName() {
        return (String)get(AppenderInfo.name);
    }

    public void setName(String name) {
        put(AppenderInfo.name, name);
    }

    public void setType(String type) {
        put(AppenderInfo.type, type);
    }

    public void setFile(String file) {
        put(AppenderInfo.file, file);
    }

    public void withDetail(String name, Object value) {
        put(name, value);
    }
}
