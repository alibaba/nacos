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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
@Component
public class A2aMigrationDefinitionReadinessValidator {
    
    private final NamespaceOperationService namespaceOperationService;
    
    private final A2aHistoricalDefinitionScanner scanner;
    
    private final A2aHistoricalDefinitionReconciler reconciler;
    
    private final A2aMigrationTargetStore targetStore;
    
    public A2aMigrationDefinitionReadinessValidator(
        NamespaceOperationService namespaceOperationService,
        A2aHistoricalDefinitionScanner scanner,
        A2aHistoricalDefinitionReconciler reconciler,
        A2aMigrationTargetStore targetStore) {
        this.namespaceOperationService = namespaceOperationService;
        this.scanner = scanner;
        this.reconciler = reconciler;
        this.targetStore = targetStore;
    }
    
    /**
     * Perform one complete, read-only Definition, Version, and Storage verification round.
     *
     * @param pageSize bounded historical page size
     * @param lease optional owner lease renewed throughout a final owner round
     * @return bounded validation result and source identities
     */
    public Result validate(int pageSize, A2aMigrationLease lease) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("Historical A2A page size must be positive");
        }
        Result result = new Result();
        try {
            List<Namespace> namespaces = namespaceOperationService.getNamespaceList();
            if (namespaces == null) {
                return result.failed("Namespace listing is unavailable");
            }
            for (Namespace namespace : namespaces) {
                renew(lease);
                validateNamespace(namespace.getNamespace(), pageSize, lease, result);
            }
        } catch (Exception e) {
            result.failed(e.getMessage());
        }
        return result;
    }
    
    private void validateNamespace(String namespaceId, int pageSize,
        A2aMigrationLease lease, Result result) {
        Set<String> sourceNames = new HashSet<String>();
        result.sourceNames.put(namespaceId, sourceNames);
        int pageNo = 1;
        int pages = 1;
        while (pageNo <= pages) {
            renew(lease);
            Page<A2aHistoricalDefinitionSnapshot> page = scanner.scanPage(namespaceId, pageNo,
                pageSize);
            pages = page.getPagesAvailable();
            if (pages == 0 && page.getTotalCount() > 0) {
                pages = (int) Math.ceil((double) page.getTotalCount() / pageSize);
            }
            for (A2aHistoricalDefinitionSnapshot snapshot : page.getPageItems()) {
                result.scanned++;
                sourceNames.add(snapshot.getSummary().getName());
                try {
                    if (!reconciler.isCurrent(snapshot) || !scanner.isCurrent(snapshot)) {
                        result.differences++;
                    }
                } catch (Exception e) {
                    result.failed(e.getMessage());
                }
            }
            pageNo++;
        }
        Set<String> migratedNames = targetStore.listMigratedAgentNames(namespaceId);
        for (String migratedName : migratedNames) {
            if (!sourceNames.contains(migratedName)) {
                result.differences++;
            }
        }
    }
    
    private void renew(A2aMigrationLease lease) {
        if (lease == null) {
            return;
        }
        lease.assertOwned();
        if (!lease.renew()) {
            throw new IllegalStateException("Historical A2A reconciliation lease lost");
        }
    }
    
    /**
     * Bounded result of one complete local target-read verification.
     */
    public static final class Result {
        
        private final Map<String, Set<String>> sourceNames =
            new HashMap<String, Set<String>>();
        
        private long scanned;
        
        private long differences;
        
        private long failed;
        
        private String lastError;
        
        public boolean isReady() {
            return differences == 0 && failed == 0;
        }
        
        public Map<String, Set<String>> getSourceNames() {
            Map<String, Set<String>> result = new HashMap<String, Set<String>>();
            for (Map.Entry<String, Set<String>> entry : sourceNames.entrySet()) {
                result.put(entry.getKey(), Collections.unmodifiableSet(
                    new HashSet<String>(entry.getValue())));
            }
            return Collections.unmodifiableMap(result);
        }
        
        public long getScanned() {
            return scanned;
        }
        
        public long getDifferences() {
            return differences;
        }
        
        public long getFailed() {
            return failed;
        }
        
        public String getLastError() {
            return lastError;
        }
        
        private Result failed(String message) {
            failed++;
            lastError = message;
            return this;
        }
    }
}
