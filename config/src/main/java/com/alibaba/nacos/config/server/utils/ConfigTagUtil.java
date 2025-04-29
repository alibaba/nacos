/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.utils;

import java.util.Arrays;

/**
 * Config Tag util.
 *
 * @author PoisonGravity
 */
public class ConfigTagUtil {
    
    public static final String VIRTUAL_SERVICE = "virtual-service";
    
    public static final String DESTINATION_RULE = "destination-rule";
    
    private static final String TAGS_DELIMITER = ",";
    
    private static final String HYPHEN = "-";
    
    /**
     * <p>Checks if config tags contains "virtual-service" or "destination-rule".</p>
     * @param configTags the tags to check
     * @return {@code true} if the config tags contains "virtual-service" or "destination-rule".
     */
    public static boolean isIstio(String configTags) {
        if (configTags == null) {
            return false;
        }
        if (configTags.isEmpty()) {
            return false;
        }
        return Arrays.stream(configTags.split(TAGS_DELIMITER))
                .map(tag -> tag.trim().replaceAll(HYPHEN, ""))
                .anyMatch(tag -> tag.equalsIgnoreCase(VIRTUAL_SERVICE.replaceAll(HYPHEN, ""))
                        || tag.equalsIgnoreCase(DESTINATION_RULE.replaceAll(HYPHEN, "")));
    }
    
    /**
     * <p>Gets the type of Istio from the config tags.</p>
     * @param configTags the tags to check
     * @return the type of Istio if it is found, {@code null} otherwise.
     * @throws IllegalArgumentException if configTags is null.
     */
    public static String getIstioType(String configTags) {
        if (configTags == null) {
            throw new IllegalArgumentException("configTags cannot be null.");
        }
    
        if (configTags.isEmpty()) {
            return null;
        }
    
        return Arrays.stream(configTags.split(TAGS_DELIMITER))
                .map(tag -> tag.trim().replaceAll(HYPHEN, ""))
                .filter(tag -> tag.equalsIgnoreCase(VIRTUAL_SERVICE.replaceAll(HYPHEN, ""))
                        || tag.equalsIgnoreCase(DESTINATION_RULE.replaceAll(HYPHEN, "")))
                .findFirst()
                .orElse(null);
    }
}
