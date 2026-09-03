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

import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2aMigrationDefinitionReadinessValidatorTest {
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    @Mock
    private A2aHistoricalDefinitionScanner scanner;
    
    @Mock
    private A2aHistoricalDefinitionReconciler reconciler;
    
    @Mock
    private A2aMigrationTargetStore targetStore;
    
    @Mock
    private A2aMigrationLease lease;
    
    private A2aMigrationDefinitionReadinessValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new A2aMigrationDefinitionReadinessValidator(namespaceOperationService,
            scanner, reconciler, targetStore);
    }
    
    @Test
    void shouldValidateEveryPageAndReturnImmutableSourceNames() throws Exception {
        A2aHistoricalDefinitionSnapshot first = snapshot("first");
        A2aHistoricalDefinitionSnapshot second = snapshot("second");
        when(namespaceOperationService.getNamespaceList())
            .thenReturn(Collections.singletonList(new Namespace("public", "public")));
        when(lease.renew()).thenReturn(true);
        when(scanner.scanPage("public", 1, 1)).thenReturn(page(2, 0, first));
        when(scanner.scanPage("public", 2, 1)).thenReturn(page(2, 0, second));
        when(reconciler.isCurrent(any())).thenReturn(true);
        when(scanner.isCurrent(any())).thenReturn(true);
        when(targetStore.listMigratedAgentNames("public"))
            .thenReturn(Set.of("first", "second"));
        A2aMigrationDefinitionReadinessValidator.Result result = validator.validate(1, lease);
        assertTrue(result.isReady());
        assertEquals(2L, result.getScanned());
        assertEquals(0L, result.getDifferences());
        assertEquals(0L, result.getFailed());
        assertEquals(Set.of("first", "second"), result.getSourceNames().get("public"));
        assertThrows(UnsupportedOperationException.class,
            () -> result.getSourceNames().put("other", Collections.emptySet()));
        assertThrows(UnsupportedOperationException.class,
            () -> result.getSourceNames().get("public").add("other"));
        verify(lease, org.mockito.Mockito.times(3)).assertOwned();
    }
    
    @Test
    void shouldCountTargetSourceAndOrphanDifferencesWithoutMutation() throws Exception {
        A2aHistoricalDefinitionSnapshot targetDiff = snapshot("target-diff");
        A2aHistoricalDefinitionSnapshot sourceDiff = snapshot("source-diff");
        A2aHistoricalDefinitionSnapshot failed = snapshot("failed");
        when(namespaceOperationService.getNamespaceList())
            .thenReturn(Collections.singletonList(new Namespace("public", "public")));
        when(scanner.scanPage("public", 1, 10))
            .thenReturn(page(3, 1, targetDiff, sourceDiff, failed));
        when(reconciler.isCurrent(targetDiff)).thenReturn(false);
        when(reconciler.isCurrent(sourceDiff)).thenReturn(true);
        when(scanner.isCurrent(sourceDiff)).thenReturn(false);
        when(reconciler.isCurrent(failed)).thenThrow(new IllegalStateException("read failed"));
        when(targetStore.listMigratedAgentNames("public"))
            .thenReturn(Set.of("target-diff", "source-diff", "failed", "orphan"));
        A2aMigrationDefinitionReadinessValidator.Result result = validator.validate(10, null);
        assertFalse(result.isReady());
        assertEquals(3L, result.getScanned());
        assertEquals(3L, result.getDifferences());
        assertEquals(1L, result.getFailed());
        assertEquals("read failed", result.getLastError());
    }
    
    @Test
    void shouldFailReadinessButValidateOtherEntriesWhenOneSourceIsInvalid() throws Exception {
        A2aHistoricalDefinitionSnapshot valid = snapshot("valid");
        A2aHistoricalDefinitionScanner.ScanPage sourcePage = page(2, 1, valid);
        sourcePage.recordFailure(new IllegalStateException("malformed historical source"));
        when(namespaceOperationService.getNamespaceList())
            .thenReturn(Collections.singletonList(new Namespace("public", "public")));
        when(scanner.scanPage("public", 1, 100)).thenReturn(sourcePage);
        when(reconciler.isCurrent(valid)).thenReturn(true);
        when(scanner.isCurrent(valid)).thenReturn(true);
        when(targetStore.listMigratedAgentNames("public")).thenReturn(Set.of("valid"));
        
        A2aMigrationDefinitionReadinessValidator.Result result =
            validator.validate(100, null);
        
        assertFalse(result.isReady());
        assertEquals(1L, result.getScanned());
        assertEquals(0L, result.getDifferences());
        assertEquals(1L, result.getFailed());
        assertEquals("malformed historical source", result.getLastError());
    }
    
    @Test
    void shouldFailClosedForNamespaceAndLeaseFailures() {
        when(namespaceOperationService.getNamespaceList()).thenReturn(null);
        A2aMigrationDefinitionReadinessValidator.Result unavailable =
            validator.validate(10, null);
        assertFalse(unavailable.isReady());
        assertEquals("Namespace listing is unavailable", unavailable.getLastError());
        verify(scanner, never()).scanPage(any(), any(Integer.class), any(Integer.class));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(0, null));
        
        when(namespaceOperationService.getNamespaceList())
            .thenReturn(Collections.singletonList(new Namespace("public", "public")));
        when(lease.renew()).thenReturn(false);
        A2aMigrationDefinitionReadinessValidator.Result leaseLost = validator.validate(10, lease);
        assertFalse(leaseLost.isReady());
        assertEquals("Historical A2A reconciliation lease lost", leaseLost.getLastError());
    }
    
    private A2aHistoricalDefinitionSnapshot snapshot(String name) {
        AgentCardVersionInfo summary = new AgentCardVersionInfo();
        summary.setName(name);
        summary.setLatestPublishedVersion("1.0.0");
        return new A2aHistoricalDefinitionSnapshot("public", name, "summary", "md5", summary,
            new LinkedHashMap<String, A2aHistoricalDefinitionSnapshot.VersionSnapshot>(),
            "fingerprint-" + name);
    }
    
    @SafeVarargs
    private final A2aHistoricalDefinitionScanner.ScanPage page(long total, int pages,
        A2aHistoricalDefinitionSnapshot... items) {
        A2aHistoricalDefinitionScanner.ScanPage result =
            new A2aHistoricalDefinitionScanner.ScanPage();
        result.setTotalCount((int) total);
        result.setPagesAvailable(pages);
        result.setPageItems(Arrays.asList(items));
        return result;
    }
}
