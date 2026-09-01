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

package com.alibaba.nacos.client.ai;

import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiHttpPublicationCoordinatorTest {
    
    @Mock
    private ScheduledExecutorService executor;
    
    @Mock
    private ScheduledFuture<?> scheduledFuture;
    
    @Mock
    private AiHttpPublicationParticipant agentParticipant;
    
    @Mock
    private AiHttpPublicationParticipant mcpParticipant;
    
    @Test
    void missingSharedClientMarksEveryModuleBeforeAnyRecovery() throws Exception {
        ArgumentCaptor<Runnable> maintenance = ArgumentCaptor.forClass(Runnable.class);
        doReturn(scheduledFuture).when(executor)
            .schedule(maintenance.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        when(agentParticipant.hasRegisteredHttpPublication()).thenReturn(true);
        when(agentParticipant.heartbeat()).thenThrow(
            new NacosException(ErrorCode.HTTP_CLIENT_NOT_FOUND.getCode(), "missing"));
        AiHttpPublicationCoordinator coordinator = new AiHttpPublicationCoordinator(executor);
        coordinator.register(agentParticipant);
        coordinator.register(mcpParticipant);
        ClientLivenessInfo liveness = new ClientLivenessInfo();
        liveness.setHeartbeatIntervalMillis(1234);
        
        coordinator.stateChanged(agentParticipant, liveness, true);
        maintenance.getValue().run();
        
        InOrder order = inOrder(agentParticipant, mcpParticipant);
        order.verify(agentParticipant).redoDirtyHttpPublications();
        order.verify(mcpParticipant).redoDirtyHttpPublications();
        order.verify(agentParticipant).heartbeat();
        order.verify(agentParticipant).markHttpPublicationsDirty();
        order.verify(mcpParticipant).markHttpPublicationsDirty();
        order.verify(agentParticipant).redoDirtyHttpPublications();
        order.verify(mcpParticipant).redoDirtyHttpPublications();
        verify(mcpParticipant, never()).heartbeat();
        verify(executor, times(2)).schedule(maintenance.capture(), eq(1234L),
            eq(TimeUnit.MILLISECONDS));
        coordinator.shutdown();
        verify(executor).shutdownNow();
    }
    
    @Test
    void noHttpPublicationStopsMaintenanceWithoutHeartbeat() throws Exception {
        ArgumentCaptor<Runnable> maintenance = ArgumentCaptor.forClass(Runnable.class);
        doReturn(scheduledFuture).when(executor)
            .schedule(maintenance.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        AiHttpPublicationCoordinator coordinator = new AiHttpPublicationCoordinator(executor);
        coordinator.register(agentParticipant);
        coordinator.stateChanged(agentParticipant, null, true);
        coordinator.stateChanged(agentParticipant, null, false);
        
        verify(agentParticipant, never()).redoDirtyHttpPublications();
        verify(agentParticipant, never()).heartbeat();
        verify(executor).schedule(maintenance.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(scheduledFuture).cancel(false);
        coordinator.shutdown();
    }
}
