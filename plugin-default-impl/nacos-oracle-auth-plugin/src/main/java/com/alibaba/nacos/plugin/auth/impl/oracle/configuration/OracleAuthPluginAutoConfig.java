/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.oracle.configuration;

import com.alibaba.nacos.persistence.configuration.condition.ConditionOnExternalStorage;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.plugin.auth.impl.condition.ConditionOnInnerDatasource;
import com.alibaba.nacos.plugin.auth.impl.oracle.OracleAuthPluginConstants;
import com.alibaba.nacos.plugin.auth.impl.oracle.persistence.OraclePermissionPersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.oracle.persistence.OracleRolePersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.oracle.persistence.OracleUserPersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Auto configuration of the Oracle auth plugin. The plugin adapts the default auth plugin's
 * persistence to Oracle storage without modifying any default class: when
 * {@link PersistenceConstant#DATASOURCE_DIALECT_TYPE_PROPERTY} is {@code oracle} on a server or
 * merged deployment with external storage, the Oracle adapted user/role/permission persist
 * services are registered as primary beans, so every by-type injection of the default persist
 * service interfaces resolves to the Oracle adapted implementations. The default beans from the
 * default auth plugin stay registered but unused, and on any non-Oracle dialect this
 * configuration backs off completely, leaving the behaviour identical to the default plugin.
 *
 * @author liam
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(name = {
    "com.alibaba.nacos.plugin.auth.impl.configuration.autoconfiguration.NacosAuthPluginInnerAutoConfig",
    "com.alibaba.nacos.plugin.auth.impl.configuration.autoconfiguration.NacosAuthPluginRemoteAutoConfig"})
@ConditionalOnProperty(name = PersistenceConstant.DATASOURCE_DIALECT_TYPE_PROPERTY,
    havingValue = OracleAuthPluginConstants.ORACLE_DATA_SOURCE_TYPE)
@Conditional(value = {ConditionOnInnerDatasource.class, ConditionOnExternalStorage.class})
public class OracleAuthPluginAutoConfig {
    
    /**
     * Oracle adapted permission persist service, overriding the default one by primary type.
     *
     * @return Oracle adapted {@link PermissionPersistService}
     */
    @Bean
    @Primary
    public PermissionPersistService oraclePermissionPersistService() {
        return new OraclePermissionPersistServiceImpl();
    }
    
    /**
     * Oracle adapted role persist service, overriding the default one by primary type.
     *
     * @return Oracle adapted {@link RolePersistService}
     */
    @Bean
    @Primary
    public RolePersistService oracleRolePersistService() {
        return new OracleRolePersistServiceImpl();
    }
    
    /**
     * Oracle adapted user persist service, overriding the default one by primary type.
     *
     * @return Oracle adapted {@link UserPersistService}
     */
    @Bean
    @Primary
    public UserPersistService oracleUserPersistService() {
        return new OracleUserPersistServiceImpl();
    }
}
