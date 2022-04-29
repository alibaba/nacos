/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.remote.control.TpsControlRuleChangeEvent;
import com.alibaba.nacos.core.remote.event.ConnectionLimitRuleChangeEvent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InternalConfigChangeNotifierTest {

    private InternalConfigChangeNotifier internalConfigChangeNotifier;

    @Mock
    private ConfigQueryRequestHandler configQueryRequestHandler;

    @Before
    public void setUp() throws IOException, NacosException {
        internalConfigChangeNotifier = new InternalConfigChangeNotifier();

        ReflectionTestUtils.setField(internalConfigChangeNotifier, "configQueryRequestHandler", configQueryRequestHandler);

        when(configQueryRequestHandler.handle(Mockito.any(), Mockito.any())).thenReturn(ConfigQueryResponse.buildSuccessResponse("content"));
    }

    @Test
    public void testOnEvent() {
        final String groupKey = GroupKey2.getKey("nacos.internal.tps.control_rule_1", "nacos", "tenant");
        final String limitGroupKey = GroupKey2.getKey("nacos.internal.tps.nacos.internal.connection.limit.rule", "nacos", "tenant");

        NotifyCenter.registerSubscriber(new Subscriber() {
            @Override
            public void onEvent(Event event) {
                ConnectionLimitRuleChangeEvent connectionLimitRuleChangeEvent = (ConnectionLimitRuleChangeEvent) event;
                Assert.assertEquals("content", connectionLimitRuleChangeEvent.getLimitRule());
            }

            @Override
            public Class<? extends Event> subscribeType() {
                return ConnectionLimitRuleChangeEvent.class;
            }
        });

        NotifyCenter.registerSubscriber(new Subscriber() {
            @Override
            public void onEvent(Event event) {
                TpsControlRuleChangeEvent tpsControlRuleChangeEvent = (TpsControlRuleChangeEvent) event;
                Assert.assertEquals("content", tpsControlRuleChangeEvent.getRuleContent());
            }

            @Override
            public Class<? extends Event> subscribeType() {
                return TpsControlRuleChangeEvent.class;
            }
        });

        internalConfigChangeNotifier.onEvent(new LocalDataChangeEvent(groupKey));
        internalConfigChangeNotifier.onEvent(new LocalDataChangeEvent(limitGroupKey));

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}