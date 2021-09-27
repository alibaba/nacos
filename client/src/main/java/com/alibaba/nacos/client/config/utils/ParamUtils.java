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

package com.alibaba.nacos.client.config.utils;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.List;

/**
 * Param check util.
 *
 * @author Nacos
 */
public class ParamUtils {
    
    private static final char[] VALID_CHARS = new char[] {'_', '-', '.', ':'};
    
    private static final String CONTENT_INVALID_MSG = "content invalid";
    
    private static final String DATAID_INVALID_MSG = "dataId invalid";
    
    private static final String TENANT_INVALID_MSG = "tenant invalid";
    
    private static final String BETAIPS_INVALID_MSG = "betaIps invalid";
    
    private static final String GROUP_INVALID_MSG = "group invalid";
    
    private static final String DATUMID_INVALID_MSG = "datumId invalid";
    
    /**
     * Check the whitelist method, the legal parameters can only contain letters, numbers, and characters in validChars, and cannot be empty.
     *
     * @param param parameter
     * @return true if valid
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
        for (char c : VALID_CHARS) {
            if (c == ch) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check Tenant, dataId and group.
     *
     * @param tenant tenant
     * @param dataId dataId
     * @param group  group
     * @throws NacosException nacos exception
     */
    public static void checkTdg(String tenant, String dataId, String group) throws NacosException {
        checkTenant(tenant);
        if (StringUtils.isBlank(dataId) || !ParamUtils.isValid(dataId)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, DATAID_INVALID_MSG);
        }
        if (StringUtils.isBlank(group) || !ParamUtils.isValid(group)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, GROUP_INVALID_MSG);
        }
    }
    
    /**
     * Check key param.
     *
     * @param dataId dataId
     * @param group  group
     * @throws NacosException nacos exception
     */
    public static void checkKeyParam(String dataId, String group) throws NacosException {
        if (StringUtils.isBlank(dataId) || !ParamUtils.isValid(dataId)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, DATAID_INVALID_MSG);
        }
        if (StringUtils.isBlank(group) || !ParamUtils.isValid(group)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, GROUP_INVALID_MSG);
        }
    }
    
    /**
     * Check key param.
     *
     * @param dataId  dataId
     * @param group   group
     * @param datumId datumId
     * @throws NacosException nacos exception
     */
    public static void checkKeyParam(String dataId, String group, String datumId) throws NacosException {
        if (StringUtils.isBlank(dataId) || !ParamUtils.isValid(dataId)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, DATAID_INVALID_MSG);
        }
        if (StringUtils.isBlank(group) || !ParamUtils.isValid(group)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, GROUP_INVALID_MSG);
        }
        if (StringUtils.isBlank(datumId) || !ParamUtils.isValid(datumId)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, DATUMID_INVALID_MSG);
        }
    }
    
    /**
     * Check key param.
     *
     * @param dataIds dataIds
     * @param group   group
     * @throws NacosException nacos exception
     */
    public static void checkKeyParam(List<String> dataIds, String group) throws NacosException {
        if (dataIds == null || dataIds.size() == 0) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "dataIds invalid");
        }
        for (String dataId : dataIds) {
            if (StringUtils.isBlank(dataId) || !ParamUtils.isValid(dataId)) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, DATAID_INVALID_MSG);
            }
        }
        if (StringUtils.isBlank(group) || !ParamUtils.isValid(group)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, GROUP_INVALID_MSG);
        }
    }
    
    /**
     * Check parameter.
     *
     * @param dataId  dataId
     * @param group   group
     * @param content content
     * @throws NacosException nacos exception
     */
    public static void checkParam(String dataId, String group, String content) throws NacosException {
        checkKeyParam(dataId, group);
        if (StringUtils.isBlank(content)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, CONTENT_INVALID_MSG);
        }
    }
    
    /**
     * Check parameter.
     *
     * @param dataId  dataId
     * @param group   group
     * @param datumId datumId
     * @param content content
     * @throws NacosException nacos exception
     */
    public static void checkParam(String dataId, String group, String datumId, String content) throws NacosException {
        checkKeyParam(dataId, group, datumId);
        if (StringUtils.isBlank(content)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, CONTENT_INVALID_MSG);
        }
    }
    
    /**
     * Check Tenant.
     *
     * @param tenant tenant
     * @throws NacosException nacos exception
     */
    public static void checkTenant(String tenant) throws NacosException {
        if (StringUtils.isBlank(tenant) || !ParamUtils.isValid(tenant)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, TENANT_INVALID_MSG);
        }
    }
    
    /**
     * Check beta ips.
     *
     * @param betaIps beta ips
     * @throws NacosException nacos exception
     */
    public static void checkBetaIps(String betaIps) throws NacosException {
        if (StringUtils.isBlank(betaIps)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, BETAIPS_INVALID_MSG);
        }
        String[] ipsArr = betaIps.split(",");
        for (String ip : ipsArr) {
            if (!InternetAddressUtil.isIP(ip)) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, BETAIPS_INVALID_MSG);
            }
        }
    }
    
    /**
     * Check content.
     *
     * @param content content
     * @throws NacosException nacos exception
     */
    public static void checkContent(String content) throws NacosException {
        if (StringUtils.isBlank(content)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, CONTENT_INVALID_MSG);
        }
    }
}
