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
 *
 */

package com.alibaba.nacos.core.namespace.initializer;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.namespace.model.NamespaceTypeEnum;
import com.alibaba.nacos.core.namespace.model.TenantInfo;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * Initializes the AI Hub namespace after the application is ready.
 * @author xiongyiping
 *
 */
@Component
public class AiHubNamespaceInitializer
        implements ApplicationListener<ApplicationReadyEvent> {
    
    private static final String HUB_NAMESPACE_NAME = "Nacos AI Resource Hub";
    
    private static final String HUB_NAMESPACE_DESCRIPTION =
            "System namespace for Nacos AI Resource Hub";
    
    private final NamespacePersistService namespacePersistService;
    
    private final NamespaceOperationService namespaceOperationService;
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    public AiHubNamespaceInitializer(NamespacePersistService namespacePersistService,
            NamespaceOperationService namespaceOperationService) {
        this.namespacePersistService = namespacePersistService;
        this.namespaceOperationService = namespaceOperationService;
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        
        try {
            ensureHubNamespace();
        } catch (RuntimeException e) {
            initialized.set(false);
            throw e;
        }
    }
    
    void ensureHubNamespace() {
        String hubNamespaceId = AiConstants.Hub.NAMESPACE_ID;
        String hubKp = String.valueOf(NamespaceTypeEnum.AI_HUB.getType());
        
        TenantInfo existingHub =
                namespacePersistService.findTenantByKp(hubKp, hubNamespaceId);
        
        if (existingHub != null) {
            Loggers.CORE.info(
                    "Nacos AI Resource Hub namespace [{}] already exists",
                    hubNamespaceId);
            return;
        }
        
        if (namespacePersistService.tenantInfoCountByTenantId(hubNamespaceId) > 0) {
            throw new IllegalStateException(
                    "Reserved Nacos AI Resource Hub namespaceId ["
                            + hubNamespaceId
                            + "] is already occupied by another namespace type");
        }
        
        try {
            namespaceOperationService.createNamespace(
                    hubNamespaceId,
                    HUB_NAMESPACE_NAME,
                    HUB_NAMESPACE_DESCRIPTION,
                    NamespaceTypeEnum.AI_HUB);
        } catch (NacosException | RuntimeException e) {
            /*
             * Another Nacos node may have inserted the same Hub namespace
             * between our existence check and insert.
             */
            TenantInfo concurrentHub =
                    namespacePersistService.findTenantByKp(hubKp, hubNamespaceId);
            
            if (concurrentHub != null) {
                Loggers.CORE.info(
                        "Nacos AI Resource Hub namespace [{}] was initialized concurrently",
                        hubNamespaceId);
                return;
            }
            
            if (namespacePersistService.tenantInfoCountByTenantId(hubNamespaceId) > 0) {
                throw new IllegalStateException(
                        "Reserved Nacos AI Resource Hub namespaceId ["
                                + hubNamespaceId
                                + "] is occupied by another namespace type",
                        e);
            }
            
            throw new IllegalStateException(
                    "Failed to initialize Nacos AI Resource Hub namespace ["
                            + hubNamespaceId + "]",
                    e);
        }
        
        TenantInfo created =
                namespacePersistService.findTenantByKp(hubKp, hubNamespaceId);
        
        if (created == null) {
            throw new IllegalStateException(
                    "Nacos AI Resource Hub namespace initialization finished "
                            + "but namespace record was not found");
        }
        
        Loggers.CORE.info(
                "Initialized Nacos AI Resource Hub namespace [{}]",
                hubNamespaceId);
    }
}
