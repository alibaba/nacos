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

/**
 * Param check rules.
 *
 * @author zhuoguang
 */
public class ParamCheckRules {
    
    public static final int MAX_NAMESPACE_SHOW_NAME_LENGTH = 256;
    
    public static final String NAMESPACE_SHOW_NAME_PATTERN_STRING = "^[^@#$%^&*]+$";
    
    public static final int MAX_NAMESPACE_ID_LENGTH = 64;
    
    public static final String NAMESPACE_ID_PATTERN_STRING = "^[\\w-]+";
    
    public static final int MAX_DATA_ID_LENGTH = 256;
    
    public static final String DATA_ID_PATTERN_STRING = "^[a-zA-Z0-9-_:\\.]*$";
    
    public static final int MAX_SERVICE_NAME_LENGTH = 512;
    
    public static final String SERVICE_NAME_PATTERN_STRING = "^(?!@).((?!@@)[^\\u4E00-\\u9FA5])*$";
    
    public static final int MAX_GROUP_LENGTH = 128;
    
    public static final String GROUP_PATTERN_STRING = "^[a-zA-Z0-9-_:\\.]*$";
    
    public static final int MAX_CLUSTER_LENGTH = 64;
    
    public static final String CLUSTER_PATTERN_STRING = "^[0-9a-zA-Z-_]+$";
    
    public static final int MAX_IP_LENGTH = 128;
    
    public static final String IP_PATTERN_STRING = "^[^\\u4E00-\\u9FA5]*$";
    
    public static final int MAX_PORT = 65535;
    
    public static final int MIN_PORT = 0;
    
    public static final int MAX_METADATA_LENGTH = 1024;
    
    
}
