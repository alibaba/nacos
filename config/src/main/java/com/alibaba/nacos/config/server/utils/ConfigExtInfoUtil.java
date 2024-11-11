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
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.fasterxml.jackson.core.type.TypeReference;
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
public class ConfigExtInfoUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigExtInfoUtil.class);
    
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
    
    private ConfigExtInfoUtil() {
    }
    
    /**
     * Extract the extInfo from advance config info.
     */
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
                if (advanceConfigInfoValue instanceof String && StringUtils.isNotBlank(
                        (String) advanceConfigInfoValue)) {
                    node.put(mappedKey, ((String) advanceConfigInfoValue).trim());
                }
            }
            
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            LOGGER.error("Failed to get extra info from advance info map", ex);
            return null;
        }
    }
    
    /**
     * Extract the extInfo from all config info.
     */
    public static String getExtInfoFromAllInfo(ConfigAllInfo configAllInfo) {
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
    
    /**
     * Extract the extInfo from gray config info.
     */
    public static String getExtInfoFromGrayInfo(String grayName, String grayRuleTmp, String oldSrcUser) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        ObjectNode grayRuleNode = OBJECT_MAPPER.createObjectNode();
        
        if (StringUtils.isNotBlank(grayName)) {
            node.put("gray_name", grayName);
        }
        
        if (StringUtils.isNotBlank(oldSrcUser)) {
            node.put("src_user", oldSrcUser);
        }
        
        if (StringUtils.isNotBlank(grayRuleTmp)) {
            try {
                JsonNode parsedGrayRuleNode = OBJECT_MAPPER.readTree(grayRuleTmp);
                if (parsedGrayRuleNode.has(Constants.GRAY_RULE_TYPE)) {
                    grayRuleNode.put(Constants.GRAY_RULE_TYPE,
                            parsedGrayRuleNode.get(Constants.GRAY_RULE_TYPE).asText());
                }
                if (parsedGrayRuleNode.has(Constants.GRAY_RULE_EXPR)) {
                    grayRuleNode.put(Constants.GRAY_RULE_EXPR,
                            parsedGrayRuleNode.get(Constants.GRAY_RULE_EXPR).asText());
                }
                if (parsedGrayRuleNode.has(Constants.GRAY_RULE_VERSION)) {
                    grayRuleNode.put(Constants.GRAY_RULE_VERSION,
                            parsedGrayRuleNode.get(Constants.GRAY_RULE_VERSION).asText());
                }
                if (parsedGrayRuleNode.has(Constants.GRAY_RULE_PRIORITY)) {
                    grayRuleNode.put(Constants.GRAY_RULE_PRIORITY,
                            parsedGrayRuleNode.get(Constants.GRAY_RULE_PRIORITY).asText());
                }
                node.put("gray_rule", grayRuleNode.toString());
            } catch (Exception ex) {
                LOGGER.error("Failed to parse gray rule as json", ex);
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
    
    /**
     * Extract grayName from extInfo.
     */
    public static String extractGrayName(String extraInfo) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> dataMap = objectMapper.readValue(extraInfo, new TypeReference<Map<String, String>>() {
            });
            return dataMap.get("gray_name");
        } catch (Exception e) {
            LogUtil.DEFAULT_LOG.error("Error extracting gray_name from extraInfo", e);
            return null;
        }
    }
    
}