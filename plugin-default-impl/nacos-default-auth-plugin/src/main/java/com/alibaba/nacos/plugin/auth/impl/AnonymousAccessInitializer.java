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

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.plugin.auth.impl.configuration.NacosAuthPluginConfigProvider;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.persistence.UserPersistService;
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordEncoderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reconciles the system-reserved anonymous user, role and initial permission asynchronously.
 *
 * @author Nacos
 */
public class AnonymousAccessInitializer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AnonymousAccessInitializer.class);
    
    private static final String DEFAULT_ANONYMOUS_PERMISSION_RESOURCE = "public:*:ai/*";
    
    private static final String DEFAULT_ANONYMOUS_PERMISSION_ACTION = "r";
    
    private static final int DEFAULT_PAGE_NO = 1;
    
    private static final long FAILURE_LOG_INTERVAL_MILLIS = 300_000L;
    
    private final NacosAuthPluginConfigProvider configProvider;
    
    private final UserPersistService userPersistService;
    
    private final RolePersistService rolePersistService;
    
    private final PermissionPersistService permissionPersistService;
    
    private final Executor executor;
    
    private final AtomicBoolean reconciling = new AtomicBoolean();
    
    private volatile AnonymousIdentityState state = AnonymousIdentityState.UNKNOWN;
    
    private volatile long lastFailureLogTime;
    
    public AnonymousAccessInitializer(NacosAuthPluginConfigProvider configProvider,
        UserPersistService userPersistService, RolePersistService rolePersistService,
        PermissionPersistService permissionPersistService) {
        this(configProvider, userPersistService, rolePersistService, permissionPersistService,
            ExecutorFactory.Managed.newSingleExecutorService("anonymous-access-reconciler",
                new NameThreadFactory("com.alibaba.nacos.auth.anonymous-reconciler")));
    }
    
    AnonymousAccessInitializer(NacosAuthPluginConfigProvider configProvider,
        UserPersistService userPersistService, RolePersistService rolePersistService,
        PermissionPersistService permissionPersistService, Executor executor) {
        this.configProvider = configProvider;
        this.userPersistService = userPersistService;
        this.rolePersistService = rolePersistService;
        this.permissionPersistService = permissionPersistService;
        this.executor = executor;
    }
    
    /**
     * Request an immediate asynchronous reconciliation after anonymous access is enabled.
     */
    public void requestReconcile() {
        if (!configProvider.getConfig().isAnonymousAiEnabled()
            || AnonymousIdentityState.READY == state) {
            return;
        }
        try {
            executor.execute(this::reconcile);
        } catch (RuntimeException e) {
            LOGGER.warn("[ANONYMOUS-INIT] Failed to schedule anonymous identity reconciliation", e);
        }
    }
    
    /**
     * Periodically retry incomplete initialization without blocking configuration application.
     */
    @Scheduled(initialDelay = 5000, fixedDelay = 60000)
    public void reconcile() {
        if (!configProvider.getConfig().isAnonymousAiEnabled()
            || AnonymousIdentityState.READY == state
            || !reconciling.compareAndSet(false, true)) {
            return;
        }
        try {
            reconcilePersistentIdentity();
            state = AnonymousIdentityState.READY;
            LOGGER.info("[ANONYMOUS-INIT] Anonymous identity is ready.");
        } catch (RuntimeException e) {
            logFailure(e);
        } finally {
            reconciling.set(false);
        }
    }
    
    private void reconcilePersistentIdentity() {
        boolean roleBindingExists = hasAnonymousRoleBinding();
        ensureAnonymousUser();
        if (roleBindingExists) {
            return;
        }
        ensureDefaultPermission();
        ensureAnonymousRoleBinding();
        if (!hasAnonymousRoleBinding()) {
            throw new IllegalStateException("Anonymous role binding was not persisted");
        }
    }
    
    private void ensureAnonymousUser() {
        User existing = userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER);
        if (existing != null) {
            return;
        }
        String randomPassword = PasswordEncoderUtil.encode(UUID.randomUUID().toString());
        try {
            userPersistService.createUser(AuthConstants.ANONYMOUS_USER, randomPassword);
        } catch (RuntimeException e) {
            if (userPersistService.findUserByUsername(AuthConstants.ANONYMOUS_USER) == null) {
                throw e;
            }
            LOGGER.debug("[ANONYMOUS-INIT] Anonymous user was created by another node.");
        }
    }
    
    private void ensureDefaultPermission() {
        if (hasDefaultPermission()) {
            return;
        }
        try {
            permissionPersistService.addPermission(AuthConstants.ANONYMOUS_ROLE,
                DEFAULT_ANONYMOUS_PERMISSION_RESOURCE, DEFAULT_ANONYMOUS_PERMISSION_ACTION);
        } catch (RuntimeException e) {
            if (!hasDefaultPermission()) {
                throw e;
            }
            LOGGER.debug("[ANONYMOUS-INIT] Anonymous permission was created by another node.");
        }
    }
    
    private void ensureAnonymousRoleBinding() {
        try {
            rolePersistService.addRole(AuthConstants.ANONYMOUS_ROLE, AuthConstants.ANONYMOUS_USER);
        } catch (RuntimeException e) {
            if (!hasAnonymousRoleBinding()) {
                throw e;
            }
            LOGGER.debug("[ANONYMOUS-INIT] Anonymous role binding was created by another node.");
        }
    }
    
    private boolean hasAnonymousRoleBinding() {
        Page<RoleInfo> page = rolePersistService.getRolesByUserNameAndRoleName(
            AuthConstants.ANONYMOUS_USER, AuthConstants.ANONYMOUS_ROLE, DEFAULT_PAGE_NO, 1);
        if (page == null || page.getPageItems() == null) {
            return false;
        }
        for (RoleInfo each : page.getPageItems()) {
            if (AuthConstants.ANONYMOUS_ROLE.equals(each.getRole())
                && AuthConstants.ANONYMOUS_USER.equals(each.getUsername())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasDefaultPermission() {
        Page<PermissionInfo> page = permissionPersistService.getPermissions(
            AuthConstants.ANONYMOUS_ROLE, DEFAULT_PAGE_NO, Integer.MAX_VALUE);
        if (page == null || page.getPageItems() == null) {
            return false;
        }
        List<PermissionInfo> permissions = page.getPageItems();
        for (PermissionInfo each : permissions) {
            if (DEFAULT_ANONYMOUS_PERMISSION_RESOURCE.equals(each.getResource())
                && DEFAULT_ANONYMOUS_PERMISSION_ACTION.equals(each.getAction())) {
                return true;
            }
        }
        return false;
    }
    
    private void logFailure(RuntimeException exception) {
        long now = System.currentTimeMillis();
        if (now - lastFailureLogTime >= FAILURE_LOG_INTERVAL_MILLIS) {
            lastFailureLogTime = now;
            LOGGER.warn("[ANONYMOUS-INIT] Failed to reconcile anonymous identity; will retry",
                exception);
        } else {
            LOGGER.debug("[ANONYMOUS-INIT] Anonymous identity reconciliation is still failing",
                exception);
        }
    }
    
    private enum AnonymousIdentityState {
        UNKNOWN,
        READY
    }
}
