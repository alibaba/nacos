/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.utils;

/**
 * Naming module code starts with 20001.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class Constants {
    
    private Constants() {}
    
    public static final String OLD_NAMING_RAFT_GROUP = "naming";
    
    public static final String NAMING_PERSISTENT_SERVICE_GROUP = "naming_persistent_service";
    
    public static final String NACOS_NAMING_USE_NEW_RAFT_FIRST = "nacos.naming.use-new-raft.first";
    
}
