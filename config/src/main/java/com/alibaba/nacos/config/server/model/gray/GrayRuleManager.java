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

package com.alibaba.nacos.config.server.model.gray;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.google.gson.Gson;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GrayRuleManager.
 *
 * @author zunfei.lzf
 */
public class GrayRuleManager {
    
    private static final Map<String, Class<?>> GRAY_RULE_MAP = new ConcurrentHashMap<>(8);
    
    public static final String SPLIT = "_";
    
    static {
        Collection<GrayRule> grayRuleCollection = NacosServiceLoader.load(GrayRule.class);
        for (GrayRule grayRule : grayRuleCollection) {
            GRAY_RULE_MAP.put(grayRule.getType() + SPLIT + grayRule.getVersion(), grayRule.getClass());
        }
    }
    
    /**
     * get class by type and version.
     *
     * @param type    type.
     * @param version version.
     * @return class.
     * @date 2024/3/14
     */
    public static Class<?> getClassByTypeAndVersion(String type, String version) {
        return GRAY_RULE_MAP.get(type + SPLIT + version);
    }
    
    /**
     * construct gray rule.
     *
     * @param configGrayPersistInfo config gray persist info.
     * @return gray rule.
     * @date 2024/3/14
     */
    public static GrayRule constructGrayRule(ConfigGrayPersistInfo configGrayPersistInfo) {
        Class<?> classByTypeAndVersion = getClassByTypeAndVersion(configGrayPersistInfo.getType(),
                configGrayPersistInfo.getVersion());
        if (classByTypeAndVersion == null) {
            return null;
        }
        try {
            Constructor<?> declaredConstructor = classByTypeAndVersion.getDeclaredConstructor(String.class, int.class);
            declaredConstructor.setAccessible(true);
            return (GrayRule) declaredConstructor.newInstance(configGrayPersistInfo.getExpr(),
                    configGrayPersistInfo.getPriority());
        } catch (Exception e) {
            throw new RuntimeException(String.format("construct gray rule failed with type[%s], version[%s].",
                    configGrayPersistInfo.getType(), configGrayPersistInfo.getVersion()), e);
        }
    }
    
    /**
     * construct config gray persist info.
     *
     * @param grayRule gray rule.
     * @return config gray persist info.
     * @date 2024/3/14
     */
    public static ConfigGrayPersistInfo constructConfigGrayPersistInfo(GrayRule grayRule) {
        return new ConfigGrayPersistInfo(grayRule.getType(), grayRule.getVersion(), grayRule.getRawGrayRuleExp(),
                grayRule.getPriority());
    }
    
    /**
     * deserialize config gray persist info.
     *
     * @param grayRuleRawStringFromDb gray rule raw string from db.
     * @return config gray persist info.
     * @date 2024/3/14
     */
    public static ConfigGrayPersistInfo deserializeConfigGrayPersistInfo(String grayRuleRawStringFromDb) {
        return (new Gson()).fromJson(grayRuleRawStringFromDb, ConfigGrayPersistInfo.class);
    }
    
    /**
     * serialize config gray persist info.
     *
     * @param configGrayPersistInfo config gray persist info.
     * @return serialized string.
     * @date 2024/3/14
     */
    public static String serializeConfigGrayPersistInfo(ConfigGrayPersistInfo configGrayPersistInfo) {
        return (new Gson()).toJson(configGrayPersistInfo);
    }
}
