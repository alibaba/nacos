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

package com.alibaba.nacos.config.server.utils;

import org.junit.Assert;
import org.junit.Test;

@SuppressWarnings("checkstyle:linelength")
public class DerbyUtilsTest {
    
    @Test
    public void testDerbySqlCorrect() {
        final String testSql = "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (1,'boot-test','ALIBABA','dept:123123123\\ngroup:123123123','2ca50d002a7dabf81497f666a7967e15','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL);";
        final String result = DerbyUtils.insertStatementCorrection(testSql);
        
        final String expect = "INSERT INTO CONFIG_INFO (ID, DATA_ID, GROUP_ID, CONTENT, MD5, GMT_CREATE, GMT_MODIFIED, SRC_USER, SRC_IP, APP_NAME, TENANT_ID, C_DESC, C_USE, EFFECT, TYPE, C_SCHEMA) VALUES (1,'boot-test','ALIBABA','dept:123123123\\ngroup:123123123','2ca50d002a7dabf81497f666a7967e15','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL)";
        Assert.assertEquals(expect, result);
    }
    
}
