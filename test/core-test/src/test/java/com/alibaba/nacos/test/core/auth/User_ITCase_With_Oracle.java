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
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {
    "server.servlet.context-path=/nacos",
    "db.num=1",
    "spring.sql.init.platform=oracle",
    "db.driverClassName[0]=oracle.jdbc.driver.OracleDriver",
    "db.url[0]=jdbc:oracle:thin:@10.19.88.60:1521:xe",
    "db.user[0]=system",
    "db.password[0]=oracle"},
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class User_ITCase_With_Oracle extends User_ITCase {
  @Override
  public String getNacosPassword() {
    return "nacos";
  }
}
