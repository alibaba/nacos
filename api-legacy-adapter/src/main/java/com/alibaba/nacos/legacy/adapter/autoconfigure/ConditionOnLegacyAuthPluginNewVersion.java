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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition to load legacy auth APIs in adapter only when:
 * <ol>
 *   <li>Auth plugin is on classpath (e.g. {@code NacosRoleService} exists)</li>
 *   <li>Old v1 RoleController / PermissionController from the plugin do NOT exist
 *       (i.e. new plugin version that moved them out)</li>
 * </ol>
 * Uses class names to avoid depending on default-auth-plugin at compile time.
 * Logs when the condition is not satisfied.
 *
 * @author xiweng.yy
 */
public class ConditionOnLegacyAuthPluginNewVersion implements Condition {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionOnLegacyAuthPluginNewVersion.class);

    private static final String NACOS_ROLE_SERVICE = "com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleService";
    private static final String PLUGIN_ROLE_CONTROLLER = "com.alibaba.nacos.plugin.auth.impl.controller.RoleController";
    private static final String PLUGIN_PERMISSION_CONTROLLER =
            "com.alibaba.nacos.plugin.auth.impl.controller.PermissionController";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (!isClassPresent(NACOS_ROLE_SERVICE)) {
            LOGGER.info("Legacy auth API in adapter not loaded: auth plugin not on classpath ({} not found)",
                    NACOS_ROLE_SERVICE);
            return false;
        }
        if (isClassPresent(PLUGIN_ROLE_CONTROLLER) || isClassPresent(PLUGIN_PERMISSION_CONTROLLER)) {
            LOGGER.info(
                    "Legacy auth API in adapter not loaded: old default-auth-plugin version detected (v1 "
                            + "RoleController or PermissionController still present). Upgrade the plugin or omit it.");
            return false;
        }
        LOGGER.info("Legacy auth API in adapter loaded: auth plugin on classpath ({})", NACOS_ROLE_SERVICE);
        return true;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
