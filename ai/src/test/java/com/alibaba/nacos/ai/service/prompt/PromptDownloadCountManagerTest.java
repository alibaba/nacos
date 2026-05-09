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

package com.alibaba.nacos.ai.service.prompt;

import com.alibaba.nacos.ai.event.PromptDownloadEvent;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.common.notify.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test for {@link PromptDownloadCountManager}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class PromptDownloadCountManagerTest {
    
    private static final String NS = "public";
    
    private static final String PROMPT_KEY = "test-prompt";
    
    private static final String PROMPT_TYPE = "prompt";
    
    private static final String VERSION_A = "0.0.1";
    
    private static final String VERSION_B = "0.0.2";
    
    @Mock
    private AiResourcePersistService aiResourcePersistService;
    
    @Mock
    private AiResourceVersionPersistService aiResourceVersionPersistService;
    
    private PromptDownloadCountManager manager;
    
    @BeforeEach
    void setUp() {
        manager = new PromptDownloadCountManager(aiResourcePersistService,
            aiResourceVersionPersistService);
    }
    
    @AfterEach
    void tearDown() {
        // shutdown triggers a final flush and deregisters the subscriber.
        // Also clear any leftover counters to avoid leaks across tests.
        try {
            manager.shutdown();
        } catch (Exception ignored) {
            // Idempotent shutdown; ignore double-close errors.
        }
    }
    
    @Test
    void testSubscribeTypesReturnsPromptDownloadEvent() {
        List<Class<? extends Event>> types = manager.subscribeTypes();
        
        assertNotNull(types);
        assertEquals(1, types.size());
        assertEquals(PromptDownloadEvent.class, types.get(0));
    }
    
    @Test
    void testOnEventAccumulatesCountAndFlushesOnShutdown() {
        // Two events for same (ns, name, version) should accumulate to count=2
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_A));
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_A));
        
        // Trigger flush by invoking shutdown (it calls flush internally).
        manager.shutdown();
        
        verify(aiResourceVersionPersistService, times(1))
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(VERSION_A), eq(2L));
        verify(aiResourcePersistService, times(1))
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(2L));
    }
    
    @Test
    void testOnEventAccumulatesPerVersionIndependently() {
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_A));
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_B));
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_B));
        
        manager.shutdown();
        
        verify(aiResourceVersionPersistService)
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(VERSION_A), eq(1L));
        verify(aiResourceVersionPersistService)
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(VERSION_B), eq(2L));
        // Two separate increments on the resource-level aggregate (one per version batch).
        verify(aiResourcePersistService, times(2))
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), anyLong());
    }
    
    @Test
    void testOnEventIgnoresUnrelatedEvents() {
        // Events of other types must not cause any counter increment or DB call.
        manager.onEvent(new UnrelatedEvent());
        manager.shutdown();
        
        verifyNoInteractions(aiResourcePersistService);
        verifyNoInteractions(aiResourceVersionPersistService);
    }
    
    @Test
    void testShutdownWithEmptyCounterDoesNotHitDb() {
        // No events received at all.
        manager.shutdown();
        
        verifyNoInteractions(aiResourcePersistService);
        verifyNoInteractions(aiResourceVersionPersistService);
    }
    
    @Test
    void testFlushPutsCountBackOnFailure() {
        // Simulate a DB failure during version-level increment.
        when(aiResourceVersionPersistService.incrementDownloadCount(eq(NS), eq(PROMPT_KEY),
            eq(PROMPT_TYPE),
            eq(VERSION_A), eq(2L))).thenThrow(new RuntimeException("simulated db error"));
        
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_A));
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_A));
        
        // First shutdown triggers flush which fails; counters should be restored.
        // We cannot observe the restored counter directly without reflection,
        // but we can verify the aggregate increment was not called (flow short-circuited on failure).
        manager.shutdown();
        
        verify(aiResourceVersionPersistService)
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(VERSION_A), eq(2L));
        // Aggregate update should NOT happen when version-level update throws.
        verify(aiResourcePersistService, never())
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), anyLong());
    }
    
    @Test
    void testShutdownIsTolerantToFlushException() {
        // If flush throws, shutdown must not propagate the exception.
        doThrow(new RuntimeException("boom")).when(aiResourceVersionPersistService)
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(VERSION_A), eq(1L));
        
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_A));
        
        // Should not throw.
        manager.shutdown();
        assertTrue(true);
    }
    
    @Test
    void testSubsequentFlushRemovesZeroCountEntry() {
        // First flush drains the counter for (NS, PROMPT_KEY, VERSION_A) into a successful DB call;
        // after the call the AtomicLong value is 0 but the entry still lives in the map.
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_A));
        manager.shutdown();
        
        // Second shutdown triggers another flush. This time the entry has count == 0 and must be removed,
        // without touching the persistence layer again.
        manager.shutdown();
        
        verify(aiResourceVersionPersistService, times(1))
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(VERSION_A), eq(1L));
        verify(aiResourcePersistService, times(1))
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L));
    }
    
    @Test
    void testPutBackCountsAreRetriedOnNextFlush() {
        // First flush: version-level increment throws. Counter should be put back.
        when(aiResourceVersionPersistService.incrementDownloadCount(eq(NS), eq(PROMPT_KEY),
            eq(PROMPT_TYPE),
            eq(VERSION_A), eq(1L))).thenThrow(new RuntimeException("simulated db error"));
        
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_A));
        manager.shutdown();
        
        // Aggregate increment must NOT have been called because the version-level update failed.
        verify(aiResourcePersistService, never())
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), anyLong());
        
        // Now reset the mock so the next flush succeeds and the put-back counter gets drained.
        reset(aiResourceVersionPersistService, aiResourcePersistService);
        
        // Second shutdown triggers another flush over the previously put-back count (= 1).
        manager.shutdown();
        
        verify(aiResourceVersionPersistService, times(1))
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(VERSION_A), eq(1L));
        verify(aiResourcePersistService, times(1))
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L));
    }
    
    @Test
    void testOnEventAfterSuccessfulFlushAccumulatesAgain() {
        // Flush once successfully.
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_A));
        manager.shutdown();
        
        // Counter is now 0 but the key entry still exists in the map.
        // New events for the same key should re-accumulate onto the existing AtomicLong,
        // and the next flush should increment the DB by the new delta only (not 0, not the old total).
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_A));
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_A));
        manager.onEvent(new PromptDownloadEvent(NS, PROMPT_KEY, VERSION_A));
        manager.shutdown();
        
        verify(aiResourceVersionPersistService, times(1))
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(VERSION_A), eq(1L));
        verify(aiResourceVersionPersistService, times(1))
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(VERSION_A), eq(3L));
        verify(aiResourcePersistService, times(1))
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(1L));
        verify(aiResourcePersistService, times(1))
            .incrementDownloadCount(eq(NS), eq(PROMPT_KEY), eq(PROMPT_TYPE), eq(3L));
    }
    
    /**
     * A dummy event that {@link PromptDownloadCountManager#onEvent(Event)} should ignore.
     */
    private static final class UnrelatedEvent extends Event {
        
        private static final long serialVersionUID = 1L;
    }
}
