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
import com.alibaba.nacos.client.utils.IPUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Param check util
 *
 * @author Nacos
 */
public class ParamUtils {

    private static char[] validChars = new char[] {'_', '-', '.', ':'};

    /**
     * 白名单的方式检查, 合法的参数只能包含字母、数字、以及validChars中的字符, 并且不能为空
     *
     * @param param
     * @return
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

    public static void checkKeyParam(String dataId, String group) throws NacosException {
        if (StringUtils.isBlank(dataId) || !ParamUtils.isValid(dataId)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "dataId invalid");
        }
        if (StringUtils.isBlank(group) || !ParamUtils.isValid(group)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "group invalid");
        }
    }

    public static void checkTDG(String tenant, String dataId, String group) throws NacosException {
        checkTenant(tenant);
        if (StringUtils.isBlank(dataId) || !ParamUtils.isValid(dataId)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "dataId invalid");
        }
        if (StringUtils.isBlank(group) || !ParamUtils.isValid(group)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "group invalid");
        }
    }

    public static void checkKeyParam(String dataId, String group, String datumId)
        throws NacosException {
        if (StringUtils.isBlank(dataId) || !ParamUtils.isValid(dataId)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "dataId invalid");
        }
        if (StringUtils.isBlank(group) || !ParamUtils.isValid(group)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "group invalid");
        }
        if (StringUtils.isBlank(datumId) || !ParamUtils.isValid(datumId)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "datumId invalid");
        }
    }

    public static void checkKeyParam(List<String> dataIds, String group) throws NacosException {
        if (dataIds == null || dataIds.size() == 0) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "dataIds invalid");
        }
        for (String dataId : dataIds) {
            if (StringUtils.isBlank(dataId) || !ParamUtils.isValid(dataId)) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "dataId invalid");
            }
        }
        if (StringUtils.isBlank(group) || !ParamUtils.isValid(group)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "group invalid");
        }
    }

    public static void checkParam(String dataId, String group, String content) throws NacosException {
        checkKeyParam(dataId, group);
        if (StringUtils.isBlank(content)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "content invalid");
        }
    }

    public static void checkParam(String dataId, String group, String datumId, String content) throws NacosException {
        checkKeyParam(dataId, group, datumId);
        if (StringUtils.isBlank(content)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "content invalid");
        }
    }

    public static void checkTenant(String tenant) throws NacosException {
        if (StringUtils.isBlank(tenant) || !ParamUtils.isValid(tenant)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "tenant invalid");
        }
    }

    public static void checkBetaIps(String betaIps) throws NacosException {
        if (StringUtils.isBlank(betaIps)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "betaIps invalid");
        }
        String[] ipsArr = betaIps.split(",");
        for (String ip : ipsArr) {
            if (!IPUtil.isIPV4(ip)) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "betaIps invalid");
            }
        }
    }

    public static void checkContent(String content) throws NacosException {
        if (StringUtils.isBlank(content)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "content invalid");
        }
    }
}
