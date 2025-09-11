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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Parameter validity check util.
 *
 * @author Nacos
 */
public class ParamUtils {
    
    private static char[] validChars = new char[] {'_', '-', '.', ':'};
    
    private static final int TAG_MAX_LEN = 16;
    
    private static final int TENANT_MAX_LEN = 128;
    
    private static final String CONFIG_TAGS = "config_tags";
    
    private static final String DESC = "desc";
    
    private static final String USE = "use";
    
    private static final String EFFECT = "effect";
    
    private static final String TYPE = "type";
    
    private static final String SCHEMA = "schema";
    
    private static final String ENCRYPTED_DATA_KEY = "encryptedDataKey";
    
    /**
     * Whitelist checks that valid parameters can only contain letters, Numbers, and characters in validChars, and
     * cannot be empty.
     */
    public static boolean isValid(String param) {
        if (param == null) {
            return false;
        }
        int length = param.length();
        for (int i = 0; i < length; i++) {
            char ch = param.charAt(i);
            if (!Character.isLetterOrDigit(ch) && !isValidChar(ch)) {
                return false;
            }
        }
        return true;
    }
    
    private static boolean isValidChar(char ch) {
        for (char c : validChars) {
            if (c == ch) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check the parameter for [v1] and [v2].
     */
    public static void checkParam(String dataId, String group, String datumId, String content) throws NacosException {
        if (StringUtils.isBlank(dataId) || !isValid(dataId.trim())) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "invalid dataId : " + dataId);
        } else if (StringUtils.isBlank(group) || !isValid(group)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "invalid group : " + group);
        } else if (StringUtils.isBlank(datumId) || !isValid(datumId)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "invalid datumId : " + datumId);
        } else if (StringUtils.isBlank(content)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "content is blank : " + content);
        } else if (content.length() > PropertyUtil.getMaxContent()) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "invalid content, over " + PropertyUtil.getMaxContent());
        }
    }
    
    /**
     * Check Config basic Parameters.
     *
     * @param dataId data Id
     * @param group  group name
     * @param namespaceId namespace Id
     */
    public static void checkParam(String dataId, String group, String namespaceId) throws NacosApiException {
        if (StringUtils.isBlank(dataId) || !isValid(dataId.trim())) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "invalid dataId : " + dataId);
        }
        if (StringUtils.isBlank(group) || !isValid(group)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "invalid group : " + group);
        }
        checkTenantV2(namespaceId);
    }
    
    /**
     * Check the tag for [v1].
     */
    public static void checkParam(String tag) {
        if (StringUtils.isNotBlank(tag)) {
            if (!isValid(tag.trim())) {
                throw new IllegalArgumentException("invalid tag : " + tag);
            }
            if (tag.length() > TAG_MAX_LEN) {
                throw new IllegalArgumentException("too long tag, over 16");
            }
        }
    }
    
    /**
     * Check the config info for [v1] and [v2].
     */
    public static void checkParam(Map<String, Object> configAdvanceInfo) throws NacosException {
        for (Map.Entry<String, Object> configAdvanceInfoTmp : configAdvanceInfo.entrySet()) {
            if (CONFIG_TAGS.equals(configAdvanceInfoTmp.getKey())) {
                if (configAdvanceInfoTmp.getValue() != null) {
                    String[] tagArr = ((String) configAdvanceInfoTmp.getValue()).split(",");
                    if (tagArr.length > 5) {
                        throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                                "too much config_tags, over 5");
                    }
                    for (String tag : tagArr) {
                        if (tag.length() > 64) {
                            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(),
                                    ErrorCode.PARAMETER_VALIDATE_ERROR, "too long tag, over 64");
                        }
                    }
                }
            } else if (DESC.equals(configAdvanceInfoTmp.getKey())) {
                if (configAdvanceInfoTmp.getValue() != null
                        && ((String) configAdvanceInfoTmp.getValue()).length() > 128) {
                    throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                            "too long desc, over 128");
                }
            } else if (USE.equals(configAdvanceInfoTmp.getKey())) {
                if (configAdvanceInfoTmp.getValue() != null
                        && ((String) configAdvanceInfoTmp.getValue()).length() > 32) {
                    throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                            "too long use, over 32");
                }
            } else if (EFFECT.equals(configAdvanceInfoTmp.getKey())) {
                if (configAdvanceInfoTmp.getValue() != null
                        && ((String) configAdvanceInfoTmp.getValue()).length() > 32) {
                    throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                            "too long effect, over 32");
                }
            } else if (TYPE.equals(configAdvanceInfoTmp.getKey())) {
                if (configAdvanceInfoTmp.getValue() != null
                        && ((String) configAdvanceInfoTmp.getValue()).length() > 32) {
                    throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                            "too long type, over 32");
                }
            } else if (SCHEMA.equals(configAdvanceInfoTmp.getKey())) {
                if (configAdvanceInfoTmp.getValue() != null
                        && ((String) configAdvanceInfoTmp.getValue()).length() > 32768) {
                    throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                            "too long schema, over 32768");
                }
            } else if (ENCRYPTED_DATA_KEY.equals(configAdvanceInfoTmp.getKey())) {
                // No verification required
            } else {
                throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "invalid param");
            }
        }
    }
    
    /**
     * Check the tag for [v2].
     */
    public static void checkParamV2(String tag) throws NacosApiException {
        if (StringUtils.isNotBlank(tag)) {
            if (!isValid(tag.trim())) {
                throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "invalid tag : " + tag);
            }
            if (tag.length() > TAG_MAX_LEN) {
                throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "too long tag, over 16");
            }
        }
    }
    
    /**
     * Check the tenant for [v1].
     */
    public static void checkTenant(String tenant) {
        if (StringUtils.isNotBlank(tenant)) {
            if (!isValid(tenant.trim())) {
                throw new IllegalArgumentException("invalid tenant");
            }
            if (tenant.length() > TENANT_MAX_LEN) {
                throw new IllegalArgumentException("too long tenant, over 128");
            }
        }
    }
    
    /**
     * Check the namespaceId for [v2].
     */
    public static void checkTenantV2(String namespaceId) throws NacosApiException {
        if (StringUtils.isNotBlank(namespaceId)) {
            if (!isValid(namespaceId.trim())) {
                throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "invalid namespaceId");
            }
            if (namespaceId.length() > TENANT_MAX_LEN) {
                throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "too long namespaceId, over 128");
            }
        }
    }
    
}
