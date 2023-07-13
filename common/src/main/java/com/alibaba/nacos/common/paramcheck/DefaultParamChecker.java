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

package com.alibaba.nacos.common.paramcheck;

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The type Default param checker.
 *
 * @author zhuoguang
 */
public class DefaultParamChecker extends AbstractParamChecker {
    
    private static final Pattern NAMESPACE_SHOW_NAME_PATTERN = Pattern.compile(ParamCheckRules.NAMESPACE_SHOW_NAME_PATTERN_STRING);
    
    private static final Pattern NAMESPACE_ID_PATTERN = Pattern.compile(ParamCheckRules.NAMESPACE_ID_PATTERN_STRING);
    
    private static final Pattern DATA_ID_PATTERN = Pattern.compile(ParamCheckRules.DATA_ID_PATTERN_STRING);
    
    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile(ParamCheckRules.SERVICE_NAME_PATTERN_STRING);
    
    private static final Pattern GROUP_PATTERN = Pattern.compile(ParamCheckRules.GROUP_PATTERN_STRING);
    
    private static final Pattern CLUSTER_PATTERN = Pattern.compile(ParamCheckRules.CLUSTER_PATTERN_STRING);
    
    private static final Pattern IP_PATTERN = Pattern.compile(ParamCheckRules.IP_PATTERN_STRING);
    
    private static final String CHECKER_TYPE = "default";
    
    @Override
    public String getCheckerType() {
        return CHECKER_TYPE;
    }
    
    @Override
    public void checkParamInfoList(List<ParamInfo> paramInfos) {
        if (paramInfos == null) {
            return;
        }
        for (ParamInfo paramInfo : paramInfos) {
            checkParamInfoFormat(paramInfo);
        }
    }
    
    /**
     * Check param info format.
     *
     * @param paramInfo the param info
     */
    public void checkParamInfoFormat(ParamInfo paramInfo) {
        if (paramInfo == null) {
            return;
        }
        checkNamespaceShowNameFormat(paramInfo.getNamespaceShowName());
        checkNamespaceIdFormat(paramInfo.getNamespaceId());
        checkDataIdFormat(paramInfo.getDataId());
        checkServiceNameFormat(paramInfo.getServiceName());
        checkGroupFormat(paramInfo.getGroup());
        checkClusterFormat(paramInfo.getClusters());
        checkSingleClusterFormat(paramInfo.getCluster());
        checkIpFormat(paramInfo.getIp());
        checkPortFormat(paramInfo.getPort());
        checkMetadataFormat(paramInfo.getMetadata());
    }
    
    /**
     * Check namespace show name format.
     *
     * @param namespaceShowName the namespace show name
     */
    public void checkNamespaceShowNameFormat(String namespaceShowName) {
        if (StringUtils.isBlank(namespaceShowName)) {
            return;
        }
        if (namespaceShowName.length() > ParamCheckRules.MAX_NAMESPACE_SHOW_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Param 'namespaceShowName' is illegal, the param length should not exceed %d.",
                            ParamCheckRules.MAX_NAMESPACE_SHOW_NAME_LENGTH));
        }
        if (!NAMESPACE_SHOW_NAME_PATTERN.matcher(namespaceShowName).matches()) {
            throw new IllegalArgumentException(
                    "Param 'namespaceShowName' is illegal, illegal characters should not appear in the param.");
        }
    }
    
    /**
     * Check namespace id format.
     *
     * @param namespaceId the namespace id
     */
    public void checkNamespaceIdFormat(String namespaceId) {
        if (StringUtils.isBlank(namespaceId)) {
            return;
        }
        if (namespaceId.length() > ParamCheckRules.MAX_NAMESPACE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Param 'namespaceId/tenant' is illegal, the param length should not exceed %d.",
                            ParamCheckRules.MAX_NAMESPACE_ID_LENGTH));
        }
        if (!NAMESPACE_ID_PATTERN.matcher(namespaceId).matches()) {
            throw new IllegalArgumentException(
                    "Param 'namespaceId/tenant' is illegal, illegal characters should not appear in the param.");
        }
    }
    
    /**
     * Check data id format.
     *
     * @param dataId the data id
     */
    public void checkDataIdFormat(String dataId) {
        if (StringUtils.isBlank(dataId)) {
            return;
        }
        if (dataId.length() > ParamCheckRules.MAX_DATA_ID_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Param 'dataId' is illegal, the param length should not exceed %d.",
                            ParamCheckRules.MAX_NAMESPACE_ID_LENGTH));
        }
        if (!DATA_ID_PATTERN.matcher(dataId).matches()) {
            throw new IllegalArgumentException(
                    "Param 'dataId' is illegal, illegal characters should not appear in the param.");
        }
    }
    
    /**
     * Check service name format.
     *
     * @param serviceName the service name
     */
    public void checkServiceNameFormat(String serviceName) {
        if (StringUtils.isBlank(serviceName)) {
            return;
        }
        if (serviceName.length() > ParamCheckRules.MAX_SERVICE_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Param 'serviceName' is illegal, the param length should not exceed %d.",
                            ParamCheckRules.MAX_SERVICE_NAME_LENGTH));
        }
        if (!SERVICE_NAME_PATTERN.matcher(serviceName).matches()) {
            throw new IllegalArgumentException(
                    "Param 'serviceName' is illegal, illegal characters should not appear in the param.");
        }
    }
    
    /**
     * Check group format.
     *
     * @param group the group
     */
    public void checkGroupFormat(String group) {
        if (StringUtils.isBlank(group)) {
            return;
        }
        if (group.length() > ParamCheckRules.MAX_GROUP_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Param 'group' is illegal, the param length should not exceed %d.",
                            ParamCheckRules.MAX_GROUP_LENGTH));
        }
        if (!GROUP_PATTERN.matcher(group).matches()) {
            throw new IllegalArgumentException(
                    "Param 'group' is illegal, illegal characters should not appear in the param.");
        }
    }
    
    /**
     * Check cluster format.
     *
     * @param clusterString the cluster string
     */
    public void checkClusterFormat(String clusterString) {
        if (StringUtils.isBlank(clusterString)) {
            return;
        }
        String[] clusters = clusterString.split(",");
        for (String cluster : clusters) {
            checkSingleClusterFormat(cluster);
        }
    }
    
    /**
     * Check single cluster format.
     *
     * @param cluster the cluster
     */
    public void checkSingleClusterFormat(String cluster) {
        if (StringUtils.isBlank(cluster)) {
            return;
        }
        
        if (cluster.length() > ParamCheckRules.MAX_CLUSTER_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Param 'cluster' is illegal, the param length should not exceed %d.",
                            ParamCheckRules.MAX_CLUSTER_LENGTH));
        }
        if (!CLUSTER_PATTERN.matcher(cluster).matches()) {
            throw new IllegalArgumentException(
                    "Param 'cluster' is illegal, illegal characters should not appear in the param.");
        }
    }
    
    /**
     * Check ip format.
     *
     * @param ip the ip
     */
    public void checkIpFormat(String ip) {
        if (StringUtils.isBlank(ip)) {
            return;
        }
        if (ip.length() > ParamCheckRules.MAX_IP_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Param 'ip' is illegal, the param length should not exceed %d.",
                            ParamCheckRules.MAX_IP_LENGTH));
        }
        if (!IP_PATTERN.matcher(ip).matches()) {
            throw new IllegalArgumentException(
                    "Param 'ip' is illegal, illegal characters should not appear in the param.");
        }
    }
    
    /**
     * Check port format.
     *
     * @param port the port
     */
    public void checkPortFormat(String port) {
        if (StringUtils.isBlank(port)) {
            return;
        }
        int portInt = 0;
        try {
            portInt = Integer.parseInt(port);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("Param 'port' is illegal, the value should be between %d and %d",
                            ParamCheckRules.MIN_PORT, ParamCheckRules.MAX_PORT));
        }
        if (portInt > ParamCheckRules.MAX_PORT || portInt < ParamCheckRules.MIN_PORT) {
            throw new IllegalArgumentException(
                    String.format("Param 'port' is illegal, the value should be between %d and %d",
                            ParamCheckRules.MIN_PORT, ParamCheckRules.MAX_PORT));
        }
    }
    
    /**
     * Check metadata format.
     *
     * @param metadata the metadata
     */
    public void checkMetadataFormat(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        int totalLength = 0;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (StringUtils.isNotBlank(entry.getKey())) {
                totalLength = totalLength + entry.getKey().length();
            }
            if (StringUtils.isNotBlank(entry.getValue())) {
                totalLength = totalLength + entry.getValue().length();
            }
        }
        if (totalLength > ParamCheckRules.MAX_METADATA_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Param 'Metadata' is illegal, the param length should not exceed %d.",
                            ParamCheckRules.MAX_METADATA_LENGTH));
        }
    }
}
