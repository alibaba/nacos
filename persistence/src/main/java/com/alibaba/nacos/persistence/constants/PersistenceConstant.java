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

package com.alibaba.nacos.persistence.constants;

/**
 * Persistence constant.
 *
 * @author xiweng.yy
 */
public class PersistenceConstant {
    
    public static final String DEFAULT_ENCODE = "UTF-8";
    
    /**
     * May be removed with the upgrade of springboot version.
     */
    public static final String DATASOURCE_PLATFORM_PROPERTY_OLD = "spring.datasource.platform";
    
    public static final String DATASOURCE_PLATFORM_PROPERTY = "spring.sql.init.platform";
    
    public static final String MYSQL = "mysql";
    
    public static final String DERBY = "derby";
    
    public static final String EMPTY_DATASOURCE_PLATFORM = "";
    
    public static final String EMBEDDED_STORAGE = "embeddedStorage";
    
    /**
     * The derby base dir.
     */
    public static final String DERBY_BASE_DIR = "derby-data";
    
    /**
     * Specifies that reads wait without timeout.
     */
    public static final String EXTEND_NEED_READ_UNTIL_HAVE_DATA = "00--0-read-join-0--00";
    
    public static final String CONFIG_MODEL_RAFT_GROUP = "nacos_config";
    
}
