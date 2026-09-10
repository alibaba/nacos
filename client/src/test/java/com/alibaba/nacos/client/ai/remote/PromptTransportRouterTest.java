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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.exception.NacosException;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptTransportRouterTest {
    
    @Mock
    private AgentGrpcTransport shared;
    
    @Mock
    private AiGrpcClient grpc;
    
    @Mock
    private AiHttpClientProxy http;
    
    @Test
    void explicitHttpPreservesConditionalQueryAndOwnsNoLifecycle() throws Exception {
        PromptTransportRouter router =
            new PromptTransportRouter(AgentTransportMode.HTTP, shared, http);
        Prompt result = new Prompt();
        when(http.queryPrompt("p", "v", "label", "md5")).thenReturn(result);
        assertSame(result, router.queryPrompt("p", "v", "label", "md5"));
        verify(shared).recordHttpSuccess(AgentGrpcTransport.Resource.PROMPT);
        verify(shared, never()).acquireProtocolNeutralClient();
        router.shutdown();
        verify(http, never()).shutdown();
        verify(grpc, never()).shutdown();
    }
    
    @Test
    void explicitGrpcNeverFallsBack() throws Exception {
        PromptTransportRouter router =
            new PromptTransportRouter(AgentTransportMode.GRPC, shared, http);
        when(shared.acquireProtocolNeutralClient()).thenReturn(grpc);
        NacosException failure =
            new NacosException(NacosException.CLIENT_DISCONNECT, "disconnected");
        when(grpc.queryPrompt("p", null, null, null)).thenThrow(failure);
        assertSame(failure,
            assertThrows(NacosException.class, () -> router.queryPrompt("p", null, null, null)));
        verify(http, never()).queryPrompt(any(), any(), any(), any());
    }
    
    @Test
    void autoUsesConnectionAndFallsBackOnlyOnConnectionEvidence() throws Exception {
        PromptTransportRouter router =
            new PromptTransportRouter(AgentTransportMode.AUTO, shared, http);
        when(shared.isAvailable(AgentGrpcTransport.Resource.PROMPT)).thenReturn(true);
        when(shared.acquireProtocolNeutralClient()).thenReturn(grpc);
        Prompt result = new Prompt();
        when(grpc.queryPrompt("p", null, null, null)).thenReturn(result);
        assertSame(result, router.queryPrompt("p", null, null, null));
        verify(shared, never()).requireGrpcClient();
        NacosException unavailable = new NacosException(NacosException.SERVER_ERROR, "unavailable",
            Status.UNAVAILABLE.asRuntimeException());
        when(grpc.queryPrompt("p", null, null, null)).thenThrow(unavailable);
        when(http.queryPrompt("p", null, null, null)).thenReturn(result);
        assertSame(result, router.queryPrompt("p", null, null, null));
        verify(shared).recordHttpSuccess(AgentGrpcTransport.Resource.PROMPT);
    }
    
    @Test
    void autoBusinessAndConditionalErrorsAreNeverReplayed() throws Exception {
        PromptTransportRouter router =
            new PromptTransportRouter(AgentTransportMode.AUTO, shared, http);
        when(shared.isAvailable(AgentGrpcTransport.Resource.PROMPT)).thenReturn(true);
        when(shared.acquireProtocolNeutralClient()).thenReturn(grpc);
        for (int code : new int[] {304, 400, 401, 403, 404, 409, 503, -503, 501, 500}) {
            NacosException failure = new NacosException(code, "failure");
            org.mockito.Mockito.doThrow(failure).when(grpc).queryPrompt("p", null, null, "md5");
            assertSame(failure, assertThrows(NacosException.class,
                () -> router.queryPrompt("p", null, null, "md5")));
        }
        verify(http, never()).queryPrompt(any(), any(), any(), any());
    }
    
    @Test
    void httpNotModifiedCountsAsSuccessfulProbeAndRetainsException() throws Exception {
        PromptTransportRouter router =
            new PromptTransportRouter(AgentTransportMode.AUTO, shared, http);
        NacosException notModified = new NacosException(NacosException.NOT_MODIFIED, "unchanged");
        when(http.queryPrompt("p", null, null, "md5")).thenThrow(notModified);
        assertSame(notModified,
            assertThrows(NacosException.class, () -> router.queryPrompt("p", null, null, "md5")));
        verify(shared).recordHttpSuccess(AgentGrpcTransport.Resource.PROMPT);
    }
    
    @ParameterizedTest
    @ValueSource(ints = {403, 404, 500, -401})
    void httpFailuresOtherThanNotModifiedDoNotCountAsSuccessfulProbe(int code) throws Exception {
        PromptTransportRouter router =
            new PromptTransportRouter(AgentTransportMode.AUTO, shared, http);
        NacosException failure = new NacosException(code, "HTTP failed");
        when(http.queryPrompt("p", "1.0.0", "stable", "md5")).thenThrow(failure);
        
        assertSame(failure, assertThrows(NacosException.class,
            () -> router.queryPrompt("p", "1.0.0", "stable", "md5")));
        verify(shared, never()).recordHttpSuccess(AgentGrpcTransport.Resource.PROMPT);
        verify(shared, never()).acquireProtocolNeutralClient();
        verifyNoInteractions(grpc);
    }
}
