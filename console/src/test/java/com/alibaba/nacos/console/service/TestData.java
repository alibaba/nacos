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

package com.alibaba.nacos.console.service;

/**
 * TestData.
 *
 * @author zhangshun
 * @version $Id: BaseTest.java,v 0.1 2020年06月04日 11:22 $Exp
 */
public class TestData {
    
    public static final String CONFIG_INFO_JSON =
            "{\n" + "    \"dataId\": \"userService\",\n" + "    \"groupId\": \"DEFAULT_GROUP\",\n"
                    + "    \"content\": \"logEnabled=true\",\n" + "    \"md5\": \"cdb2a17b65ebf01d79525962ed4b3795\",\n"
                    + "    \"gmtCreate\": 1591202059880,\n" + "    \"gmtModified\": 1591202059880,\n"
                    + "    \"srcUser\": \"zhangsan\",\n" + "    \"srcIp\": \"192.168.0.1\",\n"
                    + "    \"appName\": \"userService\",\n" + "    \"tenantId\": \"1\",\n"
                    + "    \"effect\": \"test\",\n" + "    \"type\": \"text\",\n" + "    \"cdesc\": \"用户服务\",\n"
                    + "    \"cschema\": \"test\",\n" + "    \"cuse\": \"test\"\n" + "}\n";
    
    public static final String CONFIG_INFO_AGGR_JSON =
            "{\n" + "    \"dataId\": \"userService\",\n" + "    \"groupId\": \"DEFAULT_GROUP\",\n"
                    + "    \"datumId\": \"1\",\n" + "    \"content\": \"logEnabled=true\",\n"
                    + "    \"gmtModified\": 1591202332090,\n" + "    \"appName\": \"userService\",\n"
                    + "    \"tenantId\": \"userService\"\n" + "}\n";
    
    public static final String CONFIG_INFO_BETA_JSON =
            "{\n" + "    \"dataId\": \"userService\",\n" + "    \"groupId\": \"DEFAULT_GROUP\",\n"
                    + "    \"appName\": \"userService\",\n" + "    \"content\": \"logEnabled=true\",\n"
                    + "    \"betaIps\": \"127.0.0.1\",\n" + "    \"md5\": \"cdb2a17b65ebf01d79525962ed4b3795\",\n"
                    + "    \"gmtCreate\": 1591202519327,\n" + "    \"gmtModified\": 1591202519327,\n"
                    + "    \"srcUser\": \"zhangsan\",\n" + "    \"srcIp\": \"127.0.0.1\",\n"
                    + "    \"tenantId\": \"1\"\n" + "}\n";
    
    public static final String CONFIG_INFO_TAG_JSON =
            "{\n" + "    \"dataId\": \"userService\",\n" + "    \"groupId\": \"DEFAULT_GROUP\",\n"
                    + "    \"tenantId\": \"1\",\n" + "    \"tagId\": \"1\",\n" + "    \"appName\": \"userService\",\n"
                    + "    \"content\": \"logEnabled=true\",\n" + "    \"md5\": \"cdb2a17b65ebf01d79525962ed4b3795\",\n"
                    + "    \"gmtCreate\": 1591202698062,\n" + "    \"gmtModified\": 1591202698062,\n"
                    + "    \"srcUser\": \"zhangsan\",\n" + "    \"srcIp\": \"127.0.0.1\"\n" + "}\n";
    
    public static final String CONFIG_TAGS_RELATION_JSON =
            "{\n" + "    \"id\": 1,\n" + "    \"tagName\": \"userTag\",\n" + "    \"tagType\": \"text\",\n"
                    + "    \"dataId\": \"1\",\n" + "    \"groupId\": \"1\",\n" + "    \"tenantId\": \"1\"\n" + "}\n";
    
    public static final String GROUP_CAPACITY_JSON =
            "{\n" + "    \"quota\": 10,\n" + "    \"usage\": 10,\n" + "    \"maxSize\": 100,\n"
                    + "    \"maxAggrCount\": 10,\n" + "    \"maxAggrSize\": 10,\n" + "    \"maxHistoryCount\": 10,\n"
                    + "    \"gmtCreate\": 1591202921448,\n" + "    \"gmtModified\": 1591202921448,\n"
                    + "    \"groupId\": \"\"\n" + "}\n";
    
    public static final String HIS_CONFIG_INFO_JSON =
            "{\n" + "    \"id\": 1,\n" + "    \"dataId\": \"userService\",\n" + "    \"groupId\": \"DEFAULT_GROUP\",\n"
                    + "    \"appName\": \"userService\",\n" + "    \"content\": \"logEnabled=true\",\n"
                    + "    \"md5\": \"cdb2a17b65ebf01d79525962ed4b3795\",\n" + "    \"gmtCreate\": 1591203125641,\n"
                    + "    \"gmtModified\": 1591203125641,\n" + "    \"srcUser\": \"zhangsan\",\n"
                    + "    \"srcIp\": \"127.0.0.1\",\n" + "    \"opType\": \"D\",\n" + "    \"tenantId\": \"1\"\n"
                    + "}\n";
    
    public static final String PERMISSIONS_JSON =
            "{\n" + "    \"role\": \"ROLE_ADMIN\",\n" + "    \"resource\": \":*:*\",\n" + "    \"action\": \"rw\"\n"
                    + "}\n";
    
    public static final String ROLES_JSON =
            "{\n" + "    \"username\": \"nacos\",\n" + "    \"role\": \"ROLE_ADMIN\"\n" + "}\n";
    
    public static final String TENANT_CAPACITY_JSON =
            "{\n" + "    \"quota\": 10,\n" + "    \"usage\": 10,\n" + "    \"maxSize\": 10,\n"
                    + "    \"maxAggrCount\": 10,\n" + "    \"maxAggrSize\": 10,\n" + "    \"maxHistoryCount\": 10,\n"
                    + "    \"gmtCreate\": 1591203383418,\n" + "    \"gmtModified\": 1591203383418,\n"
                    + "    \"tenantId\": \"1\"\n" + "}\n";
    
    public static final String TENANT_INFO_JSON =
            "{\n" + "    \"kp\": \"test\",\n" + "    \"tenantId\": \"test\",\n" + "    \"tenantName\": \"zhangsan\",\n"
                    + "    \"tenantDesc\": \"牛逼的大佬\",\n" + "    \"createSource\": \"test\",\n"
                    + "    \"gmtCreate\": 1591203492966,\n" + "    \"gmtModified\": 1591203492966\n" + "}\n";
    
    public static final String USERS_JSON =
            "{\n" + "    \"username\": \"nacos\",\n" + "    \"password\": \"nacos\",\n" + "    \"enabled\": 1\n"
                    + "}\n";
    
}
