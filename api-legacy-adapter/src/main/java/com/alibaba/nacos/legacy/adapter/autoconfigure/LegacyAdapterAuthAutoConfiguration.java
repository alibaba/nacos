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

package com.alibaba.nacos.legacy.adapter.autoconfigure;

import com.alibaba.nacos.legacy.adapter.auth.LegacyAdapterWebConfigForAuth;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

/**
 * Auto-configuration for legacy v1 auth APIs (user CRUD, role, permission).
 * Loaded only when (1) auth plugin is on classpath (e.g. NacosRoleService exists)
 * and (2) old v1 RoleController/PermissionController from the plugin do not exist
 * (new plugin version). Otherwise not loaded; see {@link ConditionOnLegacyAuthPluginNewVersion} for log.
 * V1 login API remains in default-auth-plugin.
 *
 * @author xiweng.yy
 */
@Configuration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@Conditional(ConditionOnLegacyAuthPluginNewVersion.class)
@Import({LegacyAdapterAuthComponentScanConfiguration.class, LegacyAdapterWebConfigForAuth.class})
public class LegacyAdapterAuthAutoConfiguration {
}
