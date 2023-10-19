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
    
    private Pattern namespaceShowNamePattern;
    
    private Pattern namespaceIdPattern;
    
    private Pattern dataIdPattern;
    
    private Pattern serviceNamePattern;
    
    private Pattern groupPattern;
    
    private Pattern clusterPattern;
    
    private Pattern ipPattern;
    
    private static final String CHECKER_TYPE = "default";
    
    @Override
    public String getCheckerType() {
        return CHECKER_TYPE;
    }
    
    @Override
    public ParamCheckResponse checkParamInfoList(List<ParamInfo> paramInfos) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (paramInfos == null) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
        }
        for (ParamInfo paramInfo : paramInfos) {
            paramCheckResponse = checkParamInfoFormat(paramInfo);
            if (!paramCheckResponse.isSuccess()) {
                return paramCheckResponse;
            }
        }
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
    
    @Override
    public void initParamCheckRule() {
        this.paramCheckRule = new ParamCheckRule();
        initFormatPattern();
    }
    
    private void initFormatPattern() {
        this.namespaceShowNamePattern = Pattern.compile(this.paramCheckRule.namespaceShowNamePatternString);
        this.namespaceIdPattern = Pattern.compile(this.paramCheckRule.namespaceIdPatternString);
        this.dataIdPattern = Pattern.compile(this.paramCheckRule.dataIdPatternString);
        this.serviceNamePattern = Pattern.compile(this.paramCheckRule.serviceNamePatternString);
        this.groupPattern = Pattern.compile(this.paramCheckRule.groupPatternString);
        this.clusterPattern = Pattern.compile(this.paramCheckRule.clusterPatternString);
        this.ipPattern = Pattern.compile(this.paramCheckRule.ipPatternString);
    }
    
    /**
     * Check param info format.
     *
     * @param paramInfo the param info
     * @return the param check response
     */
    public ParamCheckResponse checkParamInfoFormat(ParamInfo paramInfo) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (paramInfo == null) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
        }
        paramCheckResponse = checkNamespaceShowNameFormat(paramInfo.getNamespaceShowName());
        if (!paramCheckResponse.isSuccess()) {
            return paramCheckResponse;
        }
        paramCheckResponse = checkNamespaceIdFormat(paramInfo.getNamespaceId());
        if (!paramCheckResponse.isSuccess()) {
            return paramCheckResponse;
        }
        paramCheckResponse = checkDataIdFormat(paramInfo.getDataId());
        if (!paramCheckResponse.isSuccess()) {
            return paramCheckResponse;
        }
        paramCheckResponse = checkServiceNameFormat(paramInfo.getServiceName());
        if (!paramCheckResponse.isSuccess()) {
            return paramCheckResponse;
        }
        paramCheckResponse = checkGroupFormat(paramInfo.getGroup());
        if (!paramCheckResponse.isSuccess()) {
            return paramCheckResponse;
        }
        paramCheckResponse = checkClusterFormat(paramInfo.getClusters());
        if (!paramCheckResponse.isSuccess()) {
            return paramCheckResponse;
        }
        paramCheckResponse = checkSingleClusterFormat(paramInfo.getCluster());
        if (!paramCheckResponse.isSuccess()) {
            return paramCheckResponse;
        }
        paramCheckResponse = checkIpFormat(paramInfo.getIp());
        if (!paramCheckResponse.isSuccess()) {
            return paramCheckResponse;
        }
        paramCheckResponse = checkPortFormat(paramInfo.getPort());
        if (!paramCheckResponse.isSuccess()) {
            return paramCheckResponse;
        }
        paramCheckResponse = checkMetadataFormat(paramInfo.getMetadata());
        if (!paramCheckResponse.isSuccess()) {
            return paramCheckResponse;
        }
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
    
    /**
     * Check namespace show name format.
     *
     * @param namespaceShowName the namespace show name
     * @return the param check response
     */
    public ParamCheckResponse checkNamespaceShowNameFormat(String namespaceShowName) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (StringUtils.isBlank(namespaceShowName)) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
        }
        if (namespaceShowName.length() > paramCheckRule.maxNamespaceShowNameLength) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage(String.format("Param 'namespaceShowName' is illegal, the param length should not exceed %d.",
                    paramCheckRule.maxNamespaceShowNameLength));
            return paramCheckResponse;
        }
        if (!namespaceShowNamePattern.matcher(namespaceShowName).matches()) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage("Param 'namespaceShowName' is illegal, illegal characters should not appear in the param.");
            return paramCheckResponse;
        }
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
    
    /**
     * Check namespace id format.
     *
     * @param namespaceId the namespace id
     * @return the param check response
     */
    public ParamCheckResponse checkNamespaceIdFormat(String namespaceId) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (StringUtils.isBlank(namespaceId)) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
        }
        if (namespaceId.length() > paramCheckRule.maxNamespaceIdLength) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage(String.format("Param 'namespaceId/tenant' is illegal, the param length should not exceed %d.",
                    paramCheckRule.maxNamespaceIdLength));
            return paramCheckResponse;
        }
        if (!namespaceIdPattern.matcher(namespaceId).matches()) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage("Param 'namespaceId/tenant' is illegal, illegal characters should not appear in the param.");
            return paramCheckResponse;
        }
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
    
    /**
     * Check data id format.
     *
     * @param dataId the data id
     * @return the param check response
     */
    public ParamCheckResponse checkDataIdFormat(String dataId) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (StringUtils.isBlank(dataId)) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
        }
        if (dataId.length() > paramCheckRule.maxDataIdLength) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage(String.format("Param 'dataId' is illegal, the param length should not exceed %d.",
                    paramCheckRule.maxDataIdLength));
            return paramCheckResponse;
        }
        if (!dataIdPattern.matcher(dataId).matches()) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage("Param 'dataId' is illegal, illegal characters should not appear in the param.");
            return paramCheckResponse;
        }
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
    
    /**
     * Check service name format.
     *
     * @param serviceName the service name
     * @return the param check response
     */
    public ParamCheckResponse checkServiceNameFormat(String serviceName) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (StringUtils.isBlank(serviceName)) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
        }
        if (serviceName.length() > paramCheckRule.maxServiceNameLength) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage(String.format("Param 'serviceName' is illegal, the param length should not exceed %d.",
                    paramCheckRule.maxServiceNameLength));
            return paramCheckResponse;
        }
        if (!serviceNamePattern.matcher(serviceName).matches()) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage("Param 'serviceName' is illegal, illegal characters should not appear in the param.");
            return paramCheckResponse;
        }
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
    
    /**
     * Check group format.
     *
     * @param group the group
     * @return the param check response
     */
    public ParamCheckResponse checkGroupFormat(String group) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (StringUtils.isBlank(group)) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
        }
        if (group.length() > paramCheckRule.maxGroupLength) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage(String.format("Param 'group' is illegal, the param length should not exceed %d.",
                    paramCheckRule.maxGroupLength));
            return paramCheckResponse;
        }
        if (!groupPattern.matcher(group).matches()) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage("Param 'group' is illegal, illegal characters should not appear in the param.");
            return paramCheckResponse;
        }
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
    
    /**
     * Check cluster format.
     *
     * @param clusterString the cluster string
     * @return the param check response
     */
    public ParamCheckResponse checkClusterFormat(String clusterString) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (StringUtils.isBlank(clusterString)) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
        }
        String[] clusters = clusterString.split(",");
        for (String cluster : clusters) {
            paramCheckResponse = checkSingleClusterFormat(cluster);
            if (!paramCheckResponse.isSuccess()) {
                return paramCheckResponse;
            }
        }
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
    
    /**
     * Check single cluster format.
     *
     * @param cluster the cluster
     * @return the param check response
     */
    public ParamCheckResponse checkSingleClusterFormat(String cluster) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (StringUtils.isBlank(cluster)) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
        }
        
        if (cluster.length() > paramCheckRule.maxClusterLength) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage(String.format("Param 'cluster' is illegal, the param length should not exceed %d.",
                    paramCheckRule.maxClusterLength));
            return paramCheckResponse;
        }
        if (!clusterPattern.matcher(cluster).matches()) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage("Param 'cluster' is illegal, illegal characters should not appear in the param.");
            return paramCheckResponse;
        }
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
    
    /**
     * Check ip format.
     *
     * @param ip the ip
     * @return the param check response
     */
    public ParamCheckResponse checkIpFormat(String ip) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (StringUtils.isBlank(ip)) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
        }
        if (ip.length() > paramCheckRule.maxIpLength) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage(String.format("Param 'ip' is illegal, the param length should not exceed %d.",
                    paramCheckRule.maxIpLength));
            return paramCheckResponse;
        }
        if (!ipPattern.matcher(ip).matches()) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage("Param 'ip' is illegal, illegal characters should not appear in the param.");
            return paramCheckResponse;
        }
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
    
    /**
     * Check port format.
     *
     * @param port the port
     * @return the param check response
     */
    public ParamCheckResponse checkPortFormat(String port) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (StringUtils.isBlank(port)) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
        }
        int portInt = 0;
        try {
            portInt = Integer.parseInt(port);
        } catch (Exception e) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage(String.format("Param 'port' is illegal, the value should be between %d and %d.",
                    paramCheckRule.minPort, paramCheckRule.maxPort));
            return paramCheckResponse;
        }
        if (portInt > paramCheckRule.maxPort || portInt < paramCheckRule.minPort) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage(String.format("Param 'port' is illegal, the value should be between %d and %d.",
                    paramCheckRule.minPort, paramCheckRule.maxPort));
            return paramCheckResponse;
        }
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
    
    /**
     * Check metadata format.
     *
     * @param metadata the metadata
     * @return the param check response
     */
    public ParamCheckResponse checkMetadataFormat(Map<String, String> metadata) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (metadata == null || metadata.isEmpty()) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
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
        if (totalLength > paramCheckRule.maxMetadataLength) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage(String.format("Param 'Metadata' is illegal, the param length should not exceed %d.",
                    paramCheckRule.maxMetadataLength));
            return paramCheckResponse;
        }
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
}
