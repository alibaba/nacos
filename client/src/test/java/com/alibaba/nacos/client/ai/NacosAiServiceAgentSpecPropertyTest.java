/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.ai.remote.AiHttpClientProxy;
import com.alibaba.nacos.client.ai.cache.NacosMcpServerCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosAgentCardCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosPromptCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosAgentSpecCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosSkillCacheHolder;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.AfterProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentSpecListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentSpecEvent;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Property-based test for NacosAiService blank parameter validation.
 *
 * <p>Property 5: For any blank string (null, empty, whitespace-only), calling loadAgentSpec,
 * subscribeAgentSpec, unsubscribeAgentSpec with that string as agentSpecName should throw
 * NacosApiException with INVALID_PARAM error code.</p>
 *
 * <p><b>Validates: Requirements 10.3, 10.5, 10.8</b></p>
 *
 * @author kiro
 */
class NacosAiServiceAgentSpecPropertyTest {
    
    private static final AbstractNacosAgentSpecListener DUMMY_LISTENER =
        new AbstractNacosAgentSpecListener() {
            
            @Override
            public void onEvent(NacosAgentSpecEvent event) {
                // no-op
            }
        };
    
    private NacosAiService service;
    
    private final List<AutoCloseable> constructions = new ArrayList<>();
    
    @BeforeProperty
    void setUp() throws NacosException {
        constructions.add(Mockito.mockConstruction(AiGrpcClient.class));
        constructions.add(Mockito.mockConstruction(AiHttpClientProxy.class));
        constructions.add(Mockito.mockConstruction(NacosMcpServerCacheHolder.class));
        constructions.add(Mockito.mockConstruction(NacosAgentCardCacheHolder.class));
        constructions.add(Mockito.mockConstruction(NacosPromptCacheHolder.class));
        constructions.add(Mockito.mockConstruction(NacosAgentSpecCacheHolder.class));
        constructions.add(Mockito.mockConstruction(NacosSkillCacheHolder.class));
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1");
        service = new NacosAiService(properties);
    }
    
    @AfterProperty
    void tearDown() throws Exception {
        try {
            if (service != null) {
                service.shutdown();
            }
        } finally {
            for (int i = constructions.size() - 1; i >= 0; i--) {
                constructions.get(i).close();
            }
            constructions.clear();
        }
    }
    
    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.oneOf(
            Arbitraries.just(null),
            Arbitraries.just(""),
            Arbitraries.strings().whitespace().ofMinLength(1).ofMaxLength(20));
    }
    
    /**
     * Property 5: loadAgentSpec throws NacosApiException(INVALID_PARAM) for blank agentSpecName.
     *
     * <p><b>Validates: Requirements 10.3</b></p>
     */
    @Property
    void loadAgentSpecThrowsForBlankName(@ForAll("blankStrings") String blankName) {
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.loadAgentSpec(blankName));
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
    }
    
    /**
     * Property 5: subscribeAgentSpec throws NacosApiException(INVALID_PARAM) for blank agentSpecName.
     *
     * <p><b>Validates: Requirements 10.5</b></p>
     */
    @Property
    void subscribeAgentSpecThrowsForBlankName(@ForAll("blankStrings") String blankName) {
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.subscribeAgentSpec(blankName, DUMMY_LISTENER));
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
    }
    
    /**
     * Property 5: unsubscribeAgentSpec throws NacosApiException(INVALID_PARAM) for blank agentSpecName.
     *
     * <p><b>Validates: Requirements 10.8</b></p>
     */
    @Property
    void unsubscribeAgentSpecThrowsForBlankName(@ForAll("blankStrings") String blankName) {
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> service.unsubscribeAgentSpec(blankName, DUMMY_LISTENER));
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
    }
}
