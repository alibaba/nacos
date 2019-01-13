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
import java.util.List;

/**
 * @author zhuyong on 2017/06/29
 */
public class LoggerInfo extends HashMap {

    private static String level = "level";
    private static String effectiveLevel = "effectiveLevel";
    private static String additivity = "additivity";
    private static String appenders = "appenders";

    public LoggerInfo(String name, boolean additivity) {
        put(LoggerInfo.additivity, additivity);
    }

    public void setLevel(String level) {
        put(LoggerInfo.level, level);
    }

    public void setEffectiveLevel(String effectiveLevel) {
        put(LoggerInfo.effectiveLevel, effectiveLevel);
    }

    public String getLevel() {
        return (String)get(level);
    }

    public List<AppenderInfo> getAppenders() {
        return (List<AppenderInfo>)get(appenders);
    }

    public void setAppenders(List<AppenderInfo> appenders) {
        put(LoggerInfo.appenders, appenders);
    }
}
