package com.alibaba.nacos.common.paramcheck.utils;

public class ParamCheckRules {

    public static final int MAX_NAMESPACE_SHOW_NAME_LENGTH = 256;

    public static final int MAX_NAMESPACE_ID_LENGTH = 64;

    public static final String NAMESPACE_ID_PATTERN_STRING = "^[^\\u4E00-\\u9FA5]+$";

    public static final int MAX_DATA_ID_LENGTH = 512;

    public static final String DATA_ID_PATTERN_STRING = "^((?!@@)[^\\u4E00-\\u9FA5])*$";

    public static final int MAX_SERVICE_NAME_LENGTH = 512;

    public static final String SERVICE_NAME_PATTERN_STRING = "^((?!@@)[^\\u4E00-\\u9FA5])*$";

    public static final int MAX_GROUP_LENGTH = 64;

    public static final String GROUP_PATTERN_STRING = "^((?!@@)[^\\u4E00-\\u9FA5])*$";

    public static final int MAX_CLUSTER_LENGTH = 64;

    public static final String CLUSTER_PATTERN_STRING = "^[^\\u4E00-\\u9FA5,]*$";

    public static final int MAX_IP_LENGTH = 128;

    public static final String IP_PATTERN_STRING = "^[^\\u4E00-\\u9FA5]*$";

    public static final int MAX_PORT = 65535;

    public static final int MIN_PORT = 0;

    public static final int MAX_METADATA_LENGTH = 1024;


}
