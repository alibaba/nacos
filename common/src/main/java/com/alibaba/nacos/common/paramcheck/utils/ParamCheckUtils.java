package com.alibaba.nacos.common.paramcheck.utils;

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Map;
import java.util.regex.Pattern;


public class ParamCheckUtils {

    private static final Pattern NAMESPACE_ID_PATTERN = Pattern.compile(ParamCheckRules.NAMESPACE_ID_PATTERN_STRING);

    private static final Pattern DATA_ID_PATTERN = Pattern.compile(ParamCheckRules.DATA_ID_PATTERN_STRING);

    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile(ParamCheckRules.SERVICE_NAME_PATTERN_STRING);

    private static final Pattern GROUP_PATTERN = Pattern.compile(ParamCheckRules.GROUP_PATTERN_STRING);

    private static final Pattern CLUSTER_PATTERN = Pattern.compile(ParamCheckRules.CLUSTER_PATTERN_STRING);

    private static final Pattern IP_PATTERN = Pattern.compile(ParamCheckRules.IP_PATTERN_STRING);

    public static void checkParamInfoFormat(ParamInfo paramInfo) {
        if (paramInfo == null) {
            return;
        }
        checkNamespaceShowNameFormat(paramInfo.getNamespaceShowName());
        checkNamespaceIdFormat(paramInfo.getNamespaceId());
        checkDataIdFormat(paramInfo.getDataId());
        checkServiceNameFormat(paramInfo.getServiceName());
        checkGroupFormat(paramInfo.getGroup());
        checkClusterFormat(paramInfo.getCluster());
        checkIpFormat(paramInfo.getIp());
        checkPortFormat(paramInfo.getPort());
        checkMetadataFormat(paramInfo.getMetadata());
    }


    public static void checkNamespaceShowNameFormat(String namespaceShowName) {
        if (StringUtils.isBlank(namespaceShowName)) {
            return;
        }
        if (namespaceShowName.length() > ParamCheckRules.MAX_NAMESPACE_SHOW_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Param 'namespaceShowName' is illegal, the param length should not exceed %d.",
                            ParamCheckRules.MAX_NAMESPACE_SHOW_NAME_LENGTH));
        }
    }

    public static void checkNamespaceIdFormat(String namespaceId) {
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
                    "Param 'namespaceId/tenant' is illegal, Chinese characters should not appear in the param.");
        }
    }

    public static void checkDataIdFormat(String dataId) {
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
                    "Param 'dataId' is illegal, Chinese characters and '@@' should not appear in the param.");
        }
    }

    public static void checkServiceNameFormat(String serviceName) {
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
                    "Param 'serviceName' is illegal, Chinese characters and '@@' should not appear in the param.");
        }
    }

    public static void checkGroupFormat(String group) {
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
                    "Param 'group' is illegal, Chinese characters and '@@' should not appear in the param");
        }
    }

    public static void checkClusterFormat(String cluster) {
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
                    "Param 'cluster' is illegal, Chinese characters and ',' should not appear in the param");
        }
    }

    public static void checkIpFormat(String ip) {
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
                    "Param 'ip' is illegal, Chinese characters should not appear in the param");
        }
    }

    public static void checkPortFormat(String port) {
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

    public static void checkMetadataFormat(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        int total_length = 0;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (StringUtils.isNotBlank(entry.getKey())) {
                total_length = total_length + entry.getKey().length();
            }
            if (StringUtils.isNotBlank(entry.getValue())) {
                total_length = total_length + entry.getValue().length();
            }
        }
        if (total_length > ParamCheckRules.MAX_METADATA_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Param 'Metadata' is illegal, the param length should not exceed %d.",
                            ParamCheckRules.MAX_METADATA_LENGTH));
        }
    }
}
