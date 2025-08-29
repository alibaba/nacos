/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Nacos client basic parameters utils.
 *
 * @author xiweng.yy
 */
public class ClientBasicParamUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientBasicParamUtil.class);
    
    private static final Pattern PATTERN = Pattern.compile("\\$\\{[^}]+\\}");
    
    private static final int DESENSITISE_PARAMETER_MIN_LENGTH = 2;
    
    private static final int DESENSITISE_PARAMETER_KEEP_ONE_CHAR_LENGTH = 8;
    
    private static final String NACOS_CLIENT_APP_KEY = "nacos.client.appKey";
    
    private static final String NACOS_CLIENT_CONTEXT_PATH_KEY = "nacos.client.contextPath";
    
    private static final String DEFAULT_NACOS_CLIENT_CONTEXT_PATH = "nacos";
    
    private static final String NACOS_SERVER_PORT_KEY = "nacos.server.port";
    
    private static final String DEFAULT_SERVER_PORT = "8848";
    
    private static final String BLANK_STR = "";
    
    private static String defaultContextPath;
    
    private static String appKey;
    
    private static String clientVersion = "unknown";
    
    private static String serverPort;
    
    private static String defaultNodesPath = "serverlist";
    
    static {
        // Client identity information
        appKey = NacosClientProperties.PROTOTYPE.getProperty(NACOS_CLIENT_APP_KEY, BLANK_STR);
        
        defaultContextPath = NacosClientProperties.PROTOTYPE.getProperty(NACOS_CLIENT_CONTEXT_PATH_KEY,
                DEFAULT_NACOS_CLIENT_CONTEXT_PATH);
        
        serverPort = NacosClientProperties.PROTOTYPE.getProperty(NACOS_SERVER_PORT_KEY, DEFAULT_SERVER_PORT);
        LOGGER.info("[settings] [req-serv] nacos-server port:{}", serverPort);
        
        clientVersion = VersionUtils.version;
    }
    
    public static String getAppKey() {
        return appKey;
    }
    
    public static void setAppKey(String appKey) {
        ClientBasicParamUtil.appKey = appKey;
    }
    
    public static String getDefaultContextPath() {
        return defaultContextPath;
    }
    
    public static void setDefaultContextPath(String defaultContextPath) {
        ClientBasicParamUtil.defaultContextPath = defaultContextPath;
    }
    
    public static String getClientVersion() {
        return clientVersion;
    }
    
    public static void setClientVersion(String clientVersion) {
        ClientBasicParamUtil.clientVersion = clientVersion;
    }
    
    public static String getDefaultServerPort() {
        return serverPort;
    }
    
    public static String getDefaultNodesPath() {
        return defaultNodesPath;
    }
    
    public static void setDefaultNodesPath(String defaultNodesPath) {
        ClientBasicParamUtil.defaultNodesPath = defaultNodesPath;
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
        return StringUtils.isNotBlank(namespaceTmp) ? namespaceTmp.trim() : Constants.DEFAULT_NAMESPACE_ID;
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
            appendKeyParameters(result, properties, PropertyKeyConst.SERVER_ADDR, false);
            appendKeyParameters(result, properties, PropertyKeyConst.NAMESPACE, false);
            appendKeyParameters(result, properties, PropertyKeyConst.ENDPOINT, false);
            appendKeyParameters(result, properties, PropertyKeyConst.ENDPOINT_PORT, false);
            appendKeyParameters(result, properties, PropertyKeyConst.USERNAME, false);
            appendKeyParameters(result, properties, PropertyKeyConst.PASSWORD, true);
            appendKeyParameters(result, properties, PropertyKeyConst.ACCESS_KEY, true);
            appendKeyParameters(result, properties, PropertyKeyConst.SECRET_KEY, true);
            appendKeyParameters(result, properties, PropertyKeyConst.RAM_ROLE_NAME, false);
            appendKeyParameters(result, properties, PropertyKeyConst.SIGNATURE_REGION_ID, false);
        }
        return result.toString();
    }
    
    private static void appendKeyParameters(StringBuilder result, Properties properties, String propertyKey,
            boolean needDesensitise) {
        String propertyValue = properties.getProperty(propertyKey);
        if (StringUtils.isBlank(propertyValue)) {
            return;
        }
        result.append("\t").append(propertyKey).append("=")
                .append(needDesensitise ? desensitiseParameter(propertyValue) : propertyValue).append("\n");
    }
    
    /**
     * Do desensitise for parameters with `*` to replace inner content.
     *
     * @param parameterValue parameter value which need be desensitised.
     * @return desensitised parameter value.
     */
    public static String desensitiseParameter(String parameterValue) {
        if (parameterValue.length() <= DESENSITISE_PARAMETER_MIN_LENGTH) {
            return parameterValue;
        }
        if (parameterValue.length() < DESENSITISE_PARAMETER_KEEP_ONE_CHAR_LENGTH) {
            return doDesensitiseParameter(parameterValue, 1);
        }
        return doDesensitiseParameter(parameterValue, 2);
    }
    
    private static String doDesensitiseParameter(String parameterValue, int keepCharCount) {
        StringBuilder result = new StringBuilder(parameterValue);
        for (int i = keepCharCount; i < parameterValue.length() - keepCharCount; i++) {
            result.setCharAt(i, '*');
        }
        return result.toString();
    }
    
    public static String getNameSuffixByServerIps(String... serverIps) {
        StringBuilder sb = new StringBuilder();
        String split = "";
        for (String serverIp : serverIps) {
            sb.append(split);
            serverIp = serverIp.replaceAll("http(s)?://", "");
            sb.append(serverIp.replaceAll(":", "_"));
            split = "-";
        }
        return sb.toString();
    }
}
