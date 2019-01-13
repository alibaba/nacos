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
package com.alibaba.nacos.client.logger;

/**
 * 阿里中间件日志级别
 *
 * @author zhuyong 2014年3月20日 上午9:57:27
 */
public enum Level {
    /**
     * log level
     */
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),
    OFF("OFF");

    private String name;

    Level(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public static Level codeOf(String level) {
        for (Level l : Level.values()) {
            if (l.name.equals(level)) {
                return l;
            }
        }

        return OFF;
    }
}
