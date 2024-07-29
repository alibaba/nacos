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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.SystemPropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import org.slf4j.Logger;

import java.util.Properties;
import java.util.regex.Pattern;

/**
 * manage param tool.
 *
 * @author nacos
 */
public class ParamUtil {
    
    private static final Logger LOGGER = LogUtils.logger(ParamUtil.class);
    
    public static final boolean USE_ENDPOINT_PARSING_RULE_DEFAULT_VALUE = true;
    
    private static final Pattern PATTERN = Pattern.compile("\\$\\{[^}]+\\}");
    
    private static String defaultContextPath;
    
    private static String defaultNodesPath = "serverlist";
    
    private static String appKey;
    
    private static String appName;
    
    private static final String DEFAULT_SERVER_PORT = "8848";
    
    private static String serverPort;
    
    private static String clientVersion = "unknown";
    
    private static int connectTimeout;
    
    private static double perTaskConfigSize = 3000;
    
    private static final String NACOS_CLIENT_APP_KEY = "nacos.client.appKey";
    
    private static final String BLANK_STR = "";
    
    private static final String NACOS_CLIENT_CONTEXTPATH_KEY = "nacos.client.contextPath";
    
    private static final String DEFAULT_NACOS_CLIENT_CONTEXTPATH = "nacos";
    
    private static final String NACOS_SERVER_PORT_KEY = "nacos.server.port";
    
    private static final String NACOS_CONNECT_TIMEOUT_KEY = "NACOS.CONNECT.TIMEOUT";
    
    private static final String DEFAULT_NACOS_CONNECT_TIMEOUT = "1000";
    
    private static final String PER_TASK_CONFIG_SIZE_KEY = "PER_TASK_CONFIG_SIZE";
    
    private static final String DEFAULT_PER_TASK_CONFIG_SIZE_KEY = "3000";
    
    static {
        // Client identity information
        appKey = NacosClientProperties.PROTOTYPE.getProperty(NACOS_CLIENT_APP_KEY, BLANK_STR);
        
        defaultContextPath = NacosClientProperties.PROTOTYPE.getProperty(NACOS_CLIENT_CONTEXTPATH_KEY,
                DEFAULT_NACOS_CLIENT_CONTEXTPATH);
        
        appName = AppNameUtils.getAppName();
        
        serverPort = NacosClientProperties.PROTOTYPE.getProperty(NACOS_SERVER_PORT_KEY, DEFAULT_SERVER_PORT);
        LOGGER.info("[settings] [req-serv] nacos-server port:{}", serverPort);
        
        connectTimeout = initConnectionTimeout();
        LOGGER.info("[settings] [http-client] connect timeout:{}", connectTimeout);
        
        clientVersion = VersionUtils.version;
        
        perTaskConfigSize = initPerTaskConfigSize();
        LOGGER.info("PER_TASK_CONFIG_SIZE: {}", perTaskConfigSize);
    }
    
    private static int initConnectionTimeout() {
        String tmp = DEFAULT_NACOS_CONNECT_TIMEOUT;
        try {
            tmp = NacosClientProperties.PROTOTYPE.getProperty(NACOS_CONNECT_TIMEOUT_KEY, DEFAULT_NACOS_CONNECT_TIMEOUT);
            return Integer.parseInt(tmp);
        } catch (NumberFormatException e) {
            final String msg = "[http-client] invalid connect timeout:" + tmp;
            LOGGER.error("[settings] " + msg, e);
            throw new IllegalArgumentException(msg, e);
        }
    }
    
    private static double initPerTaskConfigSize() {
        try {
            return Double.parseDouble(NacosClientProperties.PROTOTYPE.getProperty(PER_TASK_CONFIG_SIZE_KEY,
                    DEFAULT_PER_TASK_CONFIG_SIZE_KEY));
        } catch (NumberFormatException e) {
            LOGGER.error("[PER_TASK_CONFIG_SIZE] PER_TASK_CONFIG_SIZE invalid", e);
            throw new IllegalArgumentException("invalid PER_TASK_CONFIG_SIZE, expected value type double", e);
        }
    }
    
    public static String getAppKey() {
        return appKey;
    }
    
    public static void setAppKey(String appKey) {
        ParamUtil.appKey = appKey;
    }
    
    public static String getAppName() {
        return appName;
    }
    
    public static void setAppName(String appName) {
        ParamUtil.appName = appName;
    }
    
    public static String getDefaultContextPath() {
        return defaultContextPath;
    }
    
    public static void setDefaultContextPath(String defaultContextPath) {
        ParamUtil.defaultContextPath = defaultContextPath;
    }
    
    public static String getClientVersion() {
        return clientVersion;
    }
    
    public static void setClientVersion(String clientVersion) {
        ParamUtil.clientVersion = clientVersion;
    }
    
    public static int getConnectTimeout() {
        return connectTimeout;
    }
    
    public static void setConnectTimeout(int connectTimeout) {
        ParamUtil.connectTimeout = connectTimeout;
    }
    
    public static double getPerTaskConfigSize() {
        return perTaskConfigSize;
    }
    
    public static void setPerTaskConfigSize(double perTaskConfigSize) {
        ParamUtil.perTaskConfigSize = perTaskConfigSize;
    }
    
    public static String getDefaultServerPort() {
        return serverPort;
    }
    
    public static String getDefaultNodesPath() {
        return defaultNodesPath;
    }
    
    public static void setDefaultNodesPath(String defaultNodesPath) {
        ParamUtil.defaultNodesPath = defaultNodesPath;
    }
    
    /**
     * Parse namespace from properties and environment.
     *
     * @param properties properties
     * @return namespace
     */
    public static String parseNamespace(NacosClientProperties properties) {
        String namespaceTmp = null;
        
        String isUseCloudNamespaceParsing = properties.getProperty(PropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING,
                properties.getProperty(SystemPropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING,
                        String.valueOf(Constants.DEFAULT_USE_CLOUD_NAMESPACE_PARSING)));
        
        if (Boolean.parseBoolean(isUseCloudNamespaceParsing)) {
            namespaceTmp = TenantUtil.getUserTenantForAcm();
            
            namespaceTmp = TemplateUtils.stringBlankAndThenExecute(namespaceTmp, () -> {
                String namespace = properties.getProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_NAMESPACE);
                return StringUtils.isNotBlank(namespace) ? namespace : StringUtils.EMPTY;
            });
        }
        
        if (StringUtils.isBlank(namespaceTmp)) {
            namespaceTmp = properties.getProperty(PropertyKeyConst.NAMESPACE);
        }
        return StringUtils.isNotBlank(namespaceTmp) ? namespaceTmp.trim() : StringUtils.EMPTY;
    }
    
    /**
     * Parse end point rule.
     *
     * @param endpointUrl endpoint url
     * @return end point rule
     */
    public static String parsingEndpointRule(String endpointUrl) {
        // If entered in the configuration file, the priority in ENV will be given priority.
        if (endpointUrl == null || !PATTERN.matcher(endpointUrl).find()) {
            // skip retrieve from system property and retrieve directly from system env
            String endpointUrlSource = NacosClientProperties.PROTOTYPE.getProperty(
                    PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL);
            if (StringUtils.isNotBlank(endpointUrlSource)) {
                endpointUrl = endpointUrlSource;
            }
            
            return StringUtils.isNotBlank(endpointUrl) ? endpointUrl : "";
        }
        
        endpointUrl = endpointUrl.substring(endpointUrl.indexOf("${") + 2, endpointUrl.lastIndexOf("}"));
        int defStartOf = endpointUrl.indexOf(":");
        String defaultEndpointUrl = null;
        if (defStartOf != -1) {
            defaultEndpointUrl = endpointUrl.substring(defStartOf + 1);
            endpointUrl = endpointUrl.substring(0, defStartOf);
        }
        
        String endpointUrlSource = TemplateUtils.stringBlankAndThenExecute(
                NacosClientProperties.PROTOTYPE.getProperty(endpointUrl),
                () -> NacosClientProperties.PROTOTYPE.getProperty(
                        PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL));
        
        if (StringUtils.isBlank(endpointUrlSource)) {
            if (StringUtils.isNotBlank(defaultEndpointUrl)) {
                endpointUrl = defaultEndpointUrl;
            }
        } else {
            endpointUrl = endpointUrlSource;
        }
        
        return StringUtils.isNotBlank(endpointUrl) ? endpointUrl : "";
    }
    
    public static final int MAX_ENV_NAME_LENGTH = 50;
    
    /**
     * simply env name if name is too long.
     *
     * @param envName env name.
     * @return
     */
    public static String simplyEnvNameIfOverLimit(String envName) {
        if (StringUtils.isNotBlank(envName) && envName.length() > MAX_ENV_NAME_LENGTH) {
            return envName.substring(0, MAX_ENV_NAME_LENGTH) + MD5Utils.md5Hex(envName, "UTF-8");
        }
        return envName;
    }
    
    public static String getInputParameters(Properties properties) {
        boolean logAllParameters = ConvertUtils.toBoolean(properties.getProperty(PropertyKeyConst.LOG_ALL_PROPERTIES),
                false);
        StringBuilder result = new StringBuilder();
        if (logAllParameters) {
            result.append(
                    "Log nacos client init properties with Full mode, This mode is only used for debugging and troubleshooting. ");
            result.append(
                    "Please close this mode by removing properties `logAllProperties` after finishing debug or troubleshoot.\n");
            result.append("Nacos client all init properties: \n");
            properties.forEach(
                    (key, value) -> result.append("\t").append(key.toString()).append("=").append(value.toString())
                            .append("\n"));
        } else {
            result.append("Nacos client key init properties: \n");
            appendKeyParameters(result, properties, PropertyKeyConst.SERVER_ADDR);
            appendKeyParameters(result, properties, PropertyKeyConst.NAMESPACE);
            appendKeyParameters(result, properties, PropertyKeyConst.ENDPOINT);
            appendKeyParameters(result, properties, PropertyKeyConst.ENDPOINT_PORT);
            appendKeyParameters(result, properties, PropertyKeyConst.USERNAME);
            appendKeyParameters(result, properties, PropertyKeyConst.PASSWORD);
            appendKeyParameters(result, properties, PropertyKeyConst.ACCESS_KEY);
            appendKeyParameters(result, properties, PropertyKeyConst.SECRET_KEY);
            appendKeyParameters(result, properties, PropertyKeyConst.RAM_ROLE_NAME);
            appendKeyParameters(result, properties, PropertyKeyConst.SIGNATURE_REGION_ID);
        }
        return result.toString();
    }
    
    private static void appendKeyParameters(StringBuilder result, Properties properties, String propertyKey) {
        String propertyValue = properties.getProperty(propertyKey);
        if (StringUtils.isBlank(propertyValue)) {
            return;
        }
        result.append("\t").append(propertyKey).append("=").append(propertyValue).append("\n");
    }
}
