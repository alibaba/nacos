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

package com.alibaba.nacos.common.trace.publisher;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class TraceEventPublisherTest {
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private SmartSubscriber smartSubscriber;
    
    private TraceEventPublisher traceEventPublisher;
    
    @Before
    public void setUp() throws Exception {
        traceEventPublisher = new TraceEventPublisher();
        traceEventPublisher.init(TraceTestEvent.class, Byte.SIZE);
    }
    
    @After
    public void tearDown() throws Exception {
        traceEventPublisher.shutdown();
    }
    
    @Test
    public void testAddSubscriber() {
        traceEventPublisher.addSubscriber(subscriber, TraceTestEvent.TraceTestEvent1.class);
        traceEventPublisher.addSubscriber(smartSubscriber, TraceTestEvent.TraceTestEvent2.class);
        TraceTestEvent.TraceTestEvent1 traceTestEvent1 = new TraceTestEvent.TraceTestEvent1();
        TraceTestEvent.TraceTestEvent2 traceTestEvent2 = new TraceTestEvent.TraceTestEvent2();
        traceEventPublisher.publish(traceTestEvent1);
        traceEventPublisher.publish(traceTestEvent2);
        ThreadUtils.sleep(2000L);
        verify(subscriber).onEvent(traceTestEvent1);
        verify(smartSubscriber).onEvent(traceTestEvent2);
    }
    
    @Test
    public void testRemoveSubscriber() {
        traceEventPublisher.addSubscriber(subscriber, TraceTestEvent.TraceTestEvent1.class);
        traceEventPublisher.addSubscriber(smartSubscriber, TraceTestEvent.TraceTestEvent1.class);
        TraceTestEvent.TraceTestEvent1 traceTestEvent1 = new TraceTestEvent.TraceTestEvent1();
        traceEventPublisher.publish(traceTestEvent1);
        ThreadUtils.sleep(2000L);
        verify(subscriber).onEvent(traceTestEvent1);
        verify(smartSubscriber).onEvent(traceTestEvent1);
        traceEventPublisher.removeSubscriber(smartSubscriber, TraceTestEvent.TraceTestEvent1.class);
        traceTestEvent1 = new TraceTestEvent.TraceTestEvent1();
        traceEventPublisher.publish(traceTestEvent1);
        ThreadUtils.sleep(500L);
        verify(subscriber).onEvent(traceTestEvent1);
        verify(smartSubscriber, never()).onEvent(traceTestEvent1);
    }
    
    @Test
    public void getStatus() throws NacosException {
        traceEventPublisher.publish(new TraceTestEvent());
        traceEventPublisher.publish(new TraceTestEvent.TraceTestEvent1());
        traceEventPublisher.publish(new TraceTestEvent.TraceTestEvent2());
        String expectedStatus = "Publisher TraceTestEvent                : shutdown=false, queue=      3/8      ";
        assertThat(traceEventPublisher.getStatus(), is(expectedStatus));
        traceEventPublisher.addSubscriber(subscriber, TraceTestEvent.TraceTestEvent1.class);
        ThreadUtils.sleep(2000L);
        expectedStatus = "Publisher TraceTestEvent                : shutdown=false, queue=      0/8      ";
        assertThat(traceEventPublisher.getStatus(), is(expectedStatus));
        traceEventPublisher.shutdown();
        expectedStatus = "Publisher TraceTestEvent                : shutdown= true, queue=      0/8      ";
        assertThat(traceEventPublisher.getStatus(), is(expectedStatus));
    }
}
