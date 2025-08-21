/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.api.model.v2;

/**
 * Supported languages for announcements.
 *
 * @author zhangyukun on:2024/9/24
 */
public enum SupportedLanguage {
    /**
     * Chinese language.
     */
    ZH_CN("zh-CN"),
    
    /**
     * English language.
     */
    EN_US("en-US");
    
    private final String language;
    
    SupportedLanguage(String language) {
        this.language = language;
    }
    
    public String getLanguage() {
        return language;
    }
    
    /**
     * Check if the given language is supported.
     *
     * @param language the language to check
     * @return true if the language is supported, false otherwise
     */
    public static boolean isSupported(String language) {
        for (SupportedLanguage lang : SupportedLanguage.values()) {
            if (lang.getLanguage().equals(language)) {
                return true;
            }
        }
        return false;
    }
}
