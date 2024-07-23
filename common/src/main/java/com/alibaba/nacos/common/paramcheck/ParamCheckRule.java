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
public class ParamCheckRule {
    
    public int maxNamespaceShowNameLength = 256;
    
    public String namespaceShowNamePatternString = "^[^@#$%^&*]+$";
    
    public int maxNamespaceIdLength = 64;
    
    public String namespaceIdPatternString = "^[\\w-]+";
    
    public int maxDataIdLength = 256;
    
    public String dataIdPatternString = "^[a-zA-Z0-9-_:\\.]*$";
    
    public int maxServiceNameLength = 512;
    
    public String serviceNamePatternString = "^(?!@).((?!@@)[^\\u4E00-\\u9FA5\\s])*$";
    
    public int maxGroupLength = 128;
    
    public String groupPatternString = "^[a-zA-Z0-9-_:\\.]*$";
    
    public int maxClusterLength = 64;
    
    public String clusterPatternString = "^[0-9a-zA-Z-_]+$";
    
    public int maxIpLength = 128;
    
    public String ipPatternString = "^[^\\u4E00-\\u9FA5\\s]*$";
    
    public int maxPort = 65535;
    
    public int minPort = 0;
    
    public int maxMetadataLength = 1024;
    
    
}
