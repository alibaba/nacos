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

package com.alibaba.nacos;

import com.alibaba.nacos.server.NacosNormalBeanTypeFilter;
import com.alibaba.nacos.sys.filter.NacosTypeExcludeFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;

/**
 * Nacos Server web starter class, which load non-console web container beans.
 *
 * @author xiweng.yy
 */
@SpringBootApplication(exclude = LdapAutoConfiguration.class)
@ComponentScan(basePackages = "com.alibaba.nacos", excludeFilters = {
        @Filter(type = FilterType.REGEX, pattern = "com\\.alibaba\\.nacos\\.console.*"),
        @Filter(type = FilterType.REGEX, pattern = "com\\.alibaba\\.nacos\\.plugin\\.auth\\.impl.*"),
        @Filter(type = FilterType.REGEX, pattern = "com\\.alibaba\\.nacos\\.mcpregistry.*"),
        @Filter(type = FilterType.CUSTOM, classes = {NacosTypeExcludeFilter.class, NacosNormalBeanTypeFilter.class})})
@PropertySource("classpath:nacos-server.properties")
public class NacosServerWebApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(NacosServerWebApplication.class, args);
    }
}
