/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.configuration.autoconfiguration;

import com.alibaba.nacos.plugin.auth.impl.condition.ConditionOnInnerDatasource;
import com.alibaba.nacos.plugin.auth.impl.configuration.core.NacosAuthPluginCoreConfig;
import com.alibaba.nacos.plugin.auth.impl.configuration.core.NacosAuthPluginInnerServiceConfig;
import com.alibaba.nacos.plugin.auth.impl.configuration.persistence.NacosAuthPluginPersistenceConfig;
import com.alibaba.nacos.plugin.auth.impl.configuration.web.NacosAuthPluginWebConfig;
import com.alibaba.nacos.plugin.auth.impl.ldap.LdapAuthPluginConfig;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Nacos auth plugin configuration for inner deployment type with `merged` or `server`.
 *
 * @author xiweng.yy
 */
@Configuration
@Conditional(ConditionOnInnerDatasource.class)
@Import({NacosAuthPluginPersistenceConfig.class, NacosAuthPluginInnerServiceConfig.class,
        NacosAuthPluginCoreConfig.class, NacosAuthPluginWebConfig.class, LdapAuthPluginConfig.class})
public class NacosAuthPluginInnerAutoConfig {

}
