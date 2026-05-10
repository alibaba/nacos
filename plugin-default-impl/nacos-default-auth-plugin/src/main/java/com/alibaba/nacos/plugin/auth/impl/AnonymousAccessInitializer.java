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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordEncoderUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Initializes the system-reserved anonymous user, role and default permission when AI anonymous access is enabled.
 * Follows the same pattern as admin user initialization in Nacos.
 *
 * @author nacos
 */
public class AnonymousAccessInitializer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AnonymousAccessInitializer.class);
    
    private static final String DEFAULT_ANONYMOUS_PERMISSION_RESOURCE = "public:*:ai/*";
    
    private static final String DEFAULT_ANONYMOUS_PERMISSION_ACTION = "r";
    
    private final AuthConfigs authConfigs;
    
    private final UserPersistService userPersistService;
    
    private final RolePersistService rolePersistService;
    
    private final PermissionPersistService permissionPersistService;
    
    public AnonymousAccessInitializer(AuthConfigs authConfigs,
        UserPersistService userPersistService,
        RolePersistService rolePersistService, PermissionPersistService permissionPersistService) {
        this.authConfigs = authConfigs;
        this.userPersistService = userPersistService;
        this.rolePersistService = rolePersistService;
        this.permissionPersistService = permissionPersistService;
    }
    
    /**
     * Initialize anonymous user, role and default permission if AI anonymous access is enabled.
     */
    @PostConstruct
    public void init() {
        if (!authConfigs.isAiAnonymousEnabled()) {
            LOGGER.info("[ANONYMOUS-INIT] AI anonymous access is disabled, skip initialization.");
            return;
        }
        try {
            ensureAnonymousUser();
            ensureAnonymousRole();
            ensureDefaultPermission();
            LOGGER
                .info("[ANONYMOUS-INIT] Anonymous user/role/permission initialized successfully.");
        } catch (Exception e) {
            LOGGER.error("[ANONYMOUS-INIT] Failed to initialize anonymous access", e);
        }
    }
    
    private void ensureAnonymousUser() {
        User existing = userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER);
        if (existing != null) {
            LOGGER.info("[ANONYMOUS-INIT] Anonymous user already exists, skip creation.");
            return;
        }
        String randomPassword = PasswordEncoderUtil.encode(UUID.randomUUID().toString());
        userPersistService.createUser(AuthConstants.ANONYMOUS_USER, randomPassword);
        LOGGER.info("[ANONYMOUS-INIT] Created anonymous user: {}", AuthConstants.ANONYMOUS_USER);
    }
    
    private void ensureAnonymousRole() {
        try {
            rolePersistService.addRole(AuthConstants.ANONYMOUS_ROLE, AuthConstants.ANONYMOUS_USER);
            LOGGER.info("[ANONYMOUS-INIT] Created anonymous role: {}",
                AuthConstants.ANONYMOUS_ROLE);
        } catch (Exception e) {
            LOGGER.debug("[ANONYMOUS-INIT] Anonymous role binding may already exist: {}",
                e.getMessage());
        }
    }
    
    private void ensureDefaultPermission() {
        try {
            permissionPersistService.addPermission(AuthConstants.ANONYMOUS_ROLE,
                DEFAULT_ANONYMOUS_PERMISSION_RESOURCE, DEFAULT_ANONYMOUS_PERMISSION_ACTION);
            LOGGER.info("[ANONYMOUS-INIT] Added default anonymous permission: {} {} {}",
                AuthConstants.ANONYMOUS_ROLE, DEFAULT_ANONYMOUS_PERMISSION_RESOURCE,
                DEFAULT_ANONYMOUS_PERMISSION_ACTION);
        } catch (Exception e) {
            LOGGER.debug("[ANONYMOUS-INIT] Default anonymous permission may already exist: {}",
                e.getMessage());
        }
    }
}
