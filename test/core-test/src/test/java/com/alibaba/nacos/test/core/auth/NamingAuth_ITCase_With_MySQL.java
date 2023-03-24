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
package com.alibaba.nacos.test.core.auth;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.auth.config.AuthConfigs;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author nkorange
 * @since 1.2.0
 */
@SpringBootTest(classes = Nacos.class, properties = {
    "server.servlet.context-path=/nacos",
    "db.num=1",
    "spring.sql.init.platform=mysql",
    "db.driverClassName[0]=com.mysql.cj.jdbc.Driver",
    "db.url.[0]=jdbc:mysql://localhost:3316/nc_config?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=GMT%2B8&allowMultiQueries=true&allowPublicKeyRetrieval=true",
    "db.user.[0]=nc_config_user",
    "db.password.[0]=nc_config_pass",
    "nacos.core.auth.enabled=false"},
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class NamingAuth_ITCase_With_MySQL extends NamingAuth_ITCase {
    @Override
    public String getNacosPassword() {
        return "123456";
    }
}
