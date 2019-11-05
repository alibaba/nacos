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
package com.alibaba.nacos.client.config.impl;

/**
 * ConfigChangeHandler
 *
 * @author rushsky518
 */
public class ConfigChangeHandler {
    private ConfigChangeHandler() {
        this.configChangeParser = new PropertiesChangeParser("properties").addNext(new YmlChangeParser("yaml"));
    }

    public static ConfigChangeHandler getInstance() {
        return ConfigChangeHandlerHolder.INSTANCE;
    }

    public static AbstractConfigChangeParser getChangeParserInstance() {
        return ConfigChangeHandlerHolder.INSTANCE.configChangeParser;
    }

    private static class ConfigChangeHandlerHolder {
        private final static ConfigChangeHandler INSTANCE = new ConfigChangeHandler();
    }

    private AbstractConfigChangeParser configChangeParser;

}
