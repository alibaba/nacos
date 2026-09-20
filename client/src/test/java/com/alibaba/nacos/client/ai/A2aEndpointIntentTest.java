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

import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2aEndpointIntentTest {
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mergeOrderDoesNotChangeRangeAndDuplicateIsReplacement(boolean reverse) throws Exception {
        A2aEndpointIntent result = register(null, endpoint(reverse ? "1.2.0" : "1.0.0", "one"));
        result = register(result, endpoint(reverse ? "1.0.0" : "1.2.0", "one"));
        result = register(result, endpoint("1.0.0", "one"));
        assertBinding(result, "1.2.0", "[1.0.0,1.2.0]");
        result = result.remove("public", "agent-a", "1.0.0");
        assertBinding(result, "1.2.0", "[1.0.0,1.2.0]");
        assertNull(result.remove("public", "agent-a", "1.2.0").batch());
    }
    
    @Test
    void removedMinimumAndMiddleDoNotShrinkAndNewReferenceExtendsRange() throws Exception {
        A2aEndpointIntent result = register(null, endpoint("1.0.0", "one"));
        result = register(result, endpoint("1.2.0", "one"));
        result = result.remove("public", "agent-a", "1.0.0");
        result = register(result, endpoint("1.1.0", "one"));
        result = result.remove("public", "agent-a", "1.1.0");
        assertBinding(result, "1.2.0", "[1.0.0,1.2.0]");
        assertSame(result, result.remove("public", "agent-a", "1.1.0"));
        result = register(result, endpoint("2.0.0", "one"));
        assertBinding(result, "2.0.0", "[1.0.0,2.0.0]");
    }
    
    @Test
    void removingMaximumChangesRuntimeButOnlyLastRemovalForgetsRange() throws Exception {
        A2aEndpointIntent result = register(null, endpoint("1.0.0", "one"));
        result = register(result, endpoint("1.2.0", "one"));
        result = result.remove("public", "agent-a", "1.2.0");
        assertBinding(result, "1.0.0", "[1.0.0,1.2.0]");
        result = result.remove("public", "agent-a", "1.0.0");
        assertNull(result.batch());
        result = register(result, endpoint("1.2.0", "one"));
        assertBinding(result, "1.2.0", "[1.2.0]");
    }
    
    @Test
    void replacementDropsUnreferencedAddressesButKeepsSharedRange() throws Exception {
        A2aEndpointIntent result =
            register(null, endpoint("1.0.0", "one"), endpoint("1.0.0", "two"),
                endpoint("1.0.0", "three"));
        result = register(result, endpoint("1.2.0", "one"));
        result = register(result, endpoint("1.0.0", "three"));
        assertEquals(2, result.batch().getEndpoints().size());
        Endpoint shared = result.batch().getEndpoints().stream()
            .filter(e -> e.getUri().contains("one")).findFirst().get();
        assertEquals("[1.0.0,1.2.0]", shared.getBindings().get(0).getVersionRange());
        result = result.remove("public", "agent-a", "1.0.0");
        assertBinding(result, "1.2.0", "[1.0.0,1.2.0]");
    }
    
    @Test
    void changedPayloadInTheOnlyReferenceIsAValidReplacement() throws Exception {
        A2aEndpointIntent result = register(null, endpoint("1.0.0", "one"));
        AgentEndpoint changed = endpoint("1.0.0", "one");
        changed.setPath("new");
        result = register(result, changed);
        assertEquals("http://one:80/new", result.batch().getEndpoints().get(0).getUri());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"path", "query", "tenant", "protocolVersion", "scheme"})
    void sameNaturalKeyConflictingPayloadRejectsWholeReplacement(String field) throws Exception {
        A2aEndpointIntent original = register(null, endpoint("1.0.0", "one"));
        AgentEndpoint changed = endpoint("1.2.0", "one");
        switch (field) {
            case "path":
                changed.setPath("changed");
                break;
            case "query":
                changed.setQuery("q=changed");
                break;
            case "tenant":
                changed.setTenant("changed");
                break;
            case "protocolVersion":
                changed.setProtocolVersion("1.0.0");
                break;
            case "scheme":
                changed.setSupportTls(true);
                break;
            default:
                throw new AssertionError(field);
        }
        assertEquals(NacosException.INVALID_PARAM, assertThrows(NacosException.class,
            () -> register(original, changed, endpoint("1.2.0", "other"))).getErrCode());
        assertBinding(original, "1.0.0", "[1.0.0]");
    }
    
    @Test
    void differentTransportHostAndPortAreSeparateEndpoints() throws Exception {
        AgentEndpoint transport = endpoint("1.2.0", "one");
        transport.setTransport("GRPC");
        AgentEndpoint port = endpoint("1.2.0", "one");
        port.setPort(81);
        A2aEndpointIntent result = register(null, endpoint("1.0.0", "one"));
        result = register(result, transport, port, endpoint("1.2.0", "two"));
        assertEquals(4, result.batch().getEndpoints().size());
    }
    
    @Test
    void canonicalHostIdentityMerges() throws Exception {
        AgentEndpoint first = endpoint("1.0.0", "EXAMPLE.COM");
        A2aEndpointIntent result = register(null, first);
        result = register(result, endpoint("1.2.0", "example.com"));
        assertBinding(result, "1.2.0", "[1.0.0,1.2.0]");
    }
    
    @Test
    void transportTokensRetainTheirCaseSensitiveNaturalIdentity() throws Exception {
        AgentEndpoint first = endpoint("1.0.0", "one");
        first.setTransport("jsonrpc");
        A2aEndpointIntent result = register(null, first);
        result = register(result, endpoint("1.2.0", "one"));
        assertEquals(2, result.batch().getEndpoints().size());
    }
    
    @Test
    void exactVersionsUseNumericAndCaseSensitivePrereleaseOrdering() throws Exception {
        A2aEndpointIntent result = register(null, endpoint("1.0.0-alpha.10", "one"));
        result = register(result, endpoint("1.0.0-alpha.2", "one"));
        result = register(result, endpoint("1.0.0-Alpha.1", "one"));
        assertBinding(result, "1.0.0-alpha.10", "[1.0.0-Alpha.1,1.0.0-alpha.10]");
        result = register(result, endpoint("1.0.0", "one"));
        assertBinding(result, "1.0.0", "[1.0.0-Alpha.1,1.0.0]");
        assertTrue(AgentValidationUtils.compareVersions("10.0.0", "2.0.0") > 0);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "v1", "01.0.0", "1.0.0+build", "1.0.0-01"})
    void invalidExactVersionIsControlled(String version) {
        assertEquals(NacosException.INVALID_PARAM,
            assertThrows(NacosException.class, () -> register(null, endpoint(version, "one")))
                .getErrCode());
    }
    
    @Test
    void nullEmptyMixedVersionAndDuplicateBatchAreRejected() {
        assertThrows(NacosException.class,
            () -> A2aEndpointIntent.replace("public", "agent-a", null, null));
        assertThrows(NacosException.class, () -> register(null));
        assertThrows(NacosException.class, () -> register(null, (AgentEndpoint) null));
        assertThrows(NacosException.class, () -> register(null, endpoint(null, "one")));
        assertThrows(NacosException.class,
            () -> register(null, endpoint("1.0.0", "one"), endpoint("2.0.0", "two")));
        assertThrows(NacosException.class,
            () -> register(null, endpoint("1.0.0", "one"), endpoint("1.0.0", "one")));
    }
    
    @Test
    void sourceObjectsAndListCannotMutateRetainedSnapshots() throws Exception {
        AgentEndpoint source = endpoint("1.0.0", "one");
        source.setTenant("tenant-a");
        List<AgentEndpoint> endpoints =
            new ArrayList<AgentEndpoint>(Collections.singletonList(source));
        A2aEndpointIntent result = A2aEndpointIntent.replace("public", "agent-a", null, endpoints);
        source.setAddress("mutated");
        source.setTenant("mutated");
        endpoints.clear();
        AgentEndpoint next = endpoint("1.2.0", "one");
        next.setTenant("tenant-a");
        result = register(result, next);
        assertBinding(result, "1.2.0", "[1.0.0,1.2.0]");
        assertEquals("tenant-a",
            result.batch().getEndpoints().get(0).getMetadata()
                .get("__nacos.agent.endpoint.tenant__"));
    }
    
    static AgentEndpoint endpoint(String version, String host) {
        AgentEndpoint result = new AgentEndpoint();
        result.setAddress(host);
        result.setPort(80);
        result.setVersion(version);
        return result;
    }
    
    private A2aEndpointIntent register(A2aEndpointIntent previous, AgentEndpoint... endpoints)
        throws Exception {
        return A2aEndpointIntent.replace("public", "agent-a", previous, Arrays.asList(endpoints));
    }
    
    private void assertBinding(A2aEndpointIntent intent, String runtime, String range) {
        assertEquals(1, intent.batch().getEndpoints().size());
        List<RuntimeVersionBinding> bindings = intent.batch().getEndpoints().get(0).getBindings();
        assertEquals(1, bindings.size());
        assertEquals(runtime, bindings.get(0).getRuntimeVersion());
        assertEquals(range, bindings.get(0).getVersionRange());
    }
}
