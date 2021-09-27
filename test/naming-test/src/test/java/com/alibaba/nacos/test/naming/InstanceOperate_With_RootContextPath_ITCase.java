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

package com.alibaba.nacos.test.naming;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.test.base.ConfigCleanUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

/**
 * Test context path is '/'.
 *
 * @see <a href="https://github.com/alibaba/nacos/issues/4181">#4171</a>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos",
        "server.port=8948"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Ignore("Nacos 2.0 use gRPC, not use http open API, so ignore it and will removed")
@Deprecated
public class InstanceOperate_With_RootContextPath_ITCase extends AbstractInstanceOperate_ITCase {
    
    @BeforeClass
    public static void beforeClass() throws IOException {
        ConfigCleanUtils.changeToNewTestNacosHome(InstanceOperate_With_RootContextPath_ITCase.class.getSimpleName());
        ConfigCleanUtils.cleanClientCache();
        EnvUtil.setPort(8948);
        
    }
    
    @AfterClass
    public static void cleanClientCache() throws Exception {
        ConfigCleanUtils.cleanClientCache();
        EnvUtil.setPort(8848);
    }
}
