/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Extra info util.
 *
 * @author Nacos
 */
public class ExtraConfigInfoUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtraConfigInfoUtil.class);
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private static final Map<String, String> EXTRA_INFO_KEYS_MAPPING = new HashMap<>();
    
    static {
        EXTRA_INFO_KEYS_MAPPING.put("type", "type");
        EXTRA_INFO_KEYS_MAPPING.put("config_tags", "config_tags");
        EXTRA_INFO_KEYS_MAPPING.put("src_user", "src_user");
        EXTRA_INFO_KEYS_MAPPING.put("desc", "c_desc");
        EXTRA_INFO_KEYS_MAPPING.put("use", "c_use");
        EXTRA_INFO_KEYS_MAPPING.put("effect", "effect");
        EXTRA_INFO_KEYS_MAPPING.put("schema", "c_schema");
    }
    
    private ExtraConfigInfoUtil() {
    }
    
    public static String getExtraInfoFromAdvanceInfoMap(Map<String, Object> advanceConfigInfoMap, String srcUser) {
        try {
            if (advanceConfigInfoMap == null || advanceConfigInfoMap.isEmpty()) {
                return null;
            }
            
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            
            if (StringUtils.isNotBlank(srcUser)) {
                node.put("src_user", srcUser);
            }
            
            for (Map.Entry<String, String> entry : EXTRA_INFO_KEYS_MAPPING.entrySet()) {
                String key = entry.getKey();
                String mappedKey = entry.getValue();
                Object advanceConfigInfoValue = advanceConfigInfoMap.get(key);
                if (advanceConfigInfoValue instanceof String && StringUtils.isNotBlank((String) advanceConfigInfoValue)) {
                    node.put(mappedKey, ((String) advanceConfigInfoValue).trim());
                }
            }
            
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            LOGGER.error("Failed to get extra info from advance info map", ex);
            return null;
        }
    }
    
    public static String getExtraInfoFromAllInfo(ConfigAllInfo configAllInfo) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        
        if (StringUtils.isNotBlank(configAllInfo.getType())) {
            node.put("type", configAllInfo.getType());
        }
        if (StringUtils.isNotBlank(configAllInfo.getConfigTags())) {
            node.put("config_tags", configAllInfo.getConfigTags());
        }
        if (StringUtils.isNotBlank(configAllInfo.getEffect())) {
            node.put("effect", configAllInfo.getEffect());
        }
        if (StringUtils.isNotBlank(configAllInfo.getCreateUser())) {
            node.put("src_user", configAllInfo.getCreateUser());
        }
        if (StringUtils.isNotBlank(configAllInfo.getDesc())) {
            node.put("c_desc", configAllInfo.getDesc());
        }
        if (StringUtils.isNotBlank(configAllInfo.getUse())) {
            node.put("c_use", configAllInfo.getUse());
        }
        if (StringUtils.isNotBlank(configAllInfo.getSchema())) {
            node.put("c_schema", configAllInfo.getSchema());
        }
        
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            LOGGER.error("Failed to get extra info from all config info", ex);
            return null;
        }
    }
    
    public static String getExtraInfoFromGrayInfo(String grayNameTmp, String grayRuleTmp, String oldSrcUser) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        
        if (StringUtils.isNotBlank(grayNameTmp)) {
            node.put("gray_name", grayNameTmp);
        }
        
        if (StringUtils.isNotBlank(oldSrcUser)) {
            node.put("src_user", oldSrcUser);
        }
        
        if (StringUtils.isNotBlank(grayRuleTmp)) {
            try {
                JsonNode grayRuleNode = OBJECT_MAPPER.readTree(grayRuleTmp);
                node.setAll((ObjectNode) grayRuleNode);
            } catch (Exception ex) {
                LOGGER.error("Failed to parse grayRuleTmp as JSON: " + grayRuleTmp, ex);
                return null;
            }
        }
        
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            LOGGER.error("Failed to serialize extra info from gray info", ex);
            return null;
        }
    }
}