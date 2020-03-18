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
package com.alibaba.nacos.client.utils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public enum ModuleEnums {

    /**
     * The default scenario
     */
    DEFAULT("default"),

    /**
     * Configuration management module
     */
    CONFIG("config"),

    /**
     * Service discovery maintenance module
     */
    MAINTAIN("maintain"),

    /**
     * Service discovery module
     */
    NAMING("naming");

    private String name;

    private static ThreadLocal<String> NOW_MODULE_NAME = new ThreadLocal<String>();

    ModuleEnums(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static ModuleEnums sourceOf(String name) {
        for (ModuleEnums item : ModuleEnums.values()) {
            if (item.name.equalsIgnoreCase(name)) {
                return item;
            }
        }
        return DEFAULT;
    }

    public static void initModuleName(ModuleEnums module) {
        NOW_MODULE_NAME.set(module.name);
    }

    public static String nowModuleName() {
        return NOW_MODULE_NAME.get();
    }

    public static void clean() {
        NOW_MODULE_NAME.remove();
    }

}
