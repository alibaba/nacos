/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.config;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.repository.embedded.DatabaseOperate;
import com.alibaba.nacos.sys.utils.DiskUtils;
import com.alibaba.nacos.test.base.ConfigCleanUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Data import integration tests.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ConfigDerbyImport_CITCase {
    
    @Autowired
    private ApplicationContext context;
    
    @BeforeClass
    public static void beforeClass() {
        ConfigCleanUtils.changeToNewTestNacosHome(ConfigDerbyImport_CITCase.class.getSimpleName());
    }
    
    @Before
    public void setUp() {
        DynamicDataSource.getInstance().getDataSource().getJdbcTemplate().execute("TRUNCATE TABLE config_info");
    }
    
    @Test()
    public void testDerbyImport() throws Throwable {
        DatabaseOperate operate = context.getBean(DatabaseOperate.class);
        File file = DiskUtils.createTmpFile("derby_import" + System.currentTimeMillis(), ".tmp");
        DiskUtils.writeFile(file, ByteUtils.toBytes(SQL_SCRIPT_CONTEXT), false);
        try {
            List<Integer> ids = operate.queryMany("SELECT id FROM config_info", new Object[]{}, Integer.class);
            for (Integer each : ids) {
                System.out.println("current id in table config_info contain: " + each);
            }
            CompletableFuture<RestResult<String>> future = operate.dataImport(file);
            RestResult<String> result = future.join();
            System.out.println(result);
            Assert.assertTrue(result.ok());
    
            final String queryDataId = "people";
            final String queryGroup = "DEFAULT_GROUP";
            final String expectContent = "people.enable=true";
    
            PersistService persistService = context.getBean(PersistService.class);
            ConfigInfo configInfo = persistService.findConfigInfo(queryDataId, queryGroup, "");
            System.out.println(configInfo);
            Assert.assertNotNull(configInfo);
            Assert.assertEquals(queryDataId, configInfo.getDataId());
            Assert.assertEquals(queryGroup, configInfo.getGroup());
            Assert.assertEquals("", configInfo.getTenant());
            Assert.assertEquals(expectContent, configInfo.getContent());
        } finally {
            DiskUtils.deleteQuietly(file);
        }
    }
    
    private static String SQL_SCRIPT_CONTEXT =
            "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (1,'boot-test','ALIBABA','dept:123123123\\ngroup:123123123','2ca50d002a7dabf81497f666a7967e15','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (2,'people','DEFAULT_GROUP','people.enable=true','d92cbf8d02080017a805b7efc4481b6c','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (3,'apple','DEFAULT_GROUP','apple:\\n    list:\\n        - 1\\n        - 2\\n        - 3\\n        - 4\\n    listMap:\\n        key-1:\\n            - 1\\n            - 2\\n            - 3\\n            - 4\\n        key-2:\\n            - aa\\n            - dd\\n            - ee\\n            - rr','4eb5a2258ba6ecddd9631ec10cf36342','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (4,'nacos.log','LOG_DEVELOP','logging.level.com.alibaba.boot.nacos.sample=error','96c1909608cad11034802b336b0c5490','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,'text',NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (5,'nacos.log','DEFAULT_GROUP','logging.level.com.alibaba.cloud.examples=error','43ed366ef7542af8bf37fb6dbe39e46d','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,'text',NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (6,'test','DEFAULT_GROUP','dept: Aliware\\ngroup: Alibaba','2f60cf534f6eb67c6c95c8e0acbcff0a','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (7,'common.properties','DEFAULT_GROUP','user.age=12\\nuser.name=liaochuntao','b68ec4349bb2a824d622c423ce3cbcce','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,'properties',NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (8,'nacos_cloud_test','DEFAULT_GROUP','user.remark=this is nacos-springboot-adaper testing-89lll\\nuser.age=20','826245312682523bca25308a96ec491c','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (9,'extension1.properties','DEFAULT_GROUP','user.age=28','4585b001cc1c4ff4ac94d8dbccdb888f','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,'properties',NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (10,'first-property-source-data-id','DEFAULT_GROUP','user.name = Mercy Ma','e36dc37603eabe6dbd08ddab5ae728e3','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (11,'before-os-env-property-source-data-id','DEFAULT_GROUP','PATH = /home/my-path','44dae1248f3b8bbc72698e2de4441cc3','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (12,'after-system-properties-property-source-data-id','DEFAULT_GROUP','user.name = mercyblitz','f6a3287654d0c79ec47dc3359ea419fa','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (13,'example','DEFAULT_GROUP','useLocalCache=true','25a822367cd73c79acc56da3c73fa07e','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (14,'people','DEVELOP','people.enable=true','d92cbf8d02080017a805b7efc4481b6c','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (15,'test_nacos_1_2_0','DEFAULT_GROUP','this.is.test=liaochuntao\\n\\nkey=value\\n\\nredis.url=127.0.0.1:3306','2482e3802f8fc5fd34545bbbbd121719','2020-04-13 13:44:43','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,NULL,NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (17,'develop_test','DEFAULT_GROUP','develop_test=develop_testdevelop_testdevelop_testdevelop_test','cc4ffd21bdd54362b84d629fd243e050','2020-04-13 13:51:48','2020-04-13 13:51:48',NULL,'127.0.0.1','','188c49ac-d06f-4abe-9d05-7bb87185ac34',NULL,NULL,NULL,'properties',NULL);\n"
                    + "INSERT INTO `config_info` (`id`, `data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`) VALUES (33,'application.properties','DEFAULT_GROUP','name=liaochuntao is man','17581188a1cdc684721dde500c693c07','2020-04-30 10:45:21','2020-04-30 10:45:21',NULL,'127.0.0.1','','',NULL,NULL,NULL,'properties',NULL);\n"
                    + "\n";
    
}
