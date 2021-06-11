/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.event.publisher;

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
public class NamingEventPublisherTest {
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private SmartSubscriber smartSubscriber;
    
    private NamingEventPublisher namingEventPublisher;
    
    @Before
    public void setUp() throws Exception {
        namingEventPublisher = new NamingEventPublisher();
        namingEventPublisher.init(TestEvent.class, Byte.SIZE);
    }
    
    @After
    public void tearDown() throws Exception {
        namingEventPublisher.shutdown();
    }
    
    @Test
    public void testAddSubscriber() {
        namingEventPublisher.addSubscriber(subscriber, TestEvent.TestEvent1.class);
        namingEventPublisher.addSubscriber(smartSubscriber, TestEvent.TestEvent2.class);
        TestEvent.TestEvent1 testEvent1 = new TestEvent.TestEvent1();
        TestEvent.TestEvent2 testEvent2 = new TestEvent.TestEvent2();
        namingEventPublisher.publish(testEvent1);
        namingEventPublisher.publish(testEvent2);
        ThreadUtils.sleep(2000L);
        verify(subscriber).onEvent(testEvent1);
        verify(smartSubscriber).onEvent(testEvent2);
    }
    
    @Test
    public void testRemoveSubscriber() {
        namingEventPublisher.addSubscriber(subscriber, TestEvent.TestEvent1.class);
        namingEventPublisher.addSubscriber(smartSubscriber, TestEvent.TestEvent1.class);
        TestEvent.TestEvent1 testEvent1 = new TestEvent.TestEvent1();
        namingEventPublisher.publish(testEvent1);
        ThreadUtils.sleep(2000L);
        verify(subscriber).onEvent(testEvent1);
        verify(smartSubscriber).onEvent(testEvent1);
        namingEventPublisher.removeSubscriber(smartSubscriber, TestEvent.TestEvent1.class);
        testEvent1 = new TestEvent.TestEvent1();
        namingEventPublisher.publish(testEvent1);
        ThreadUtils.sleep(500L);
        verify(subscriber).onEvent(testEvent1);
        verify(smartSubscriber, never()).onEvent(testEvent1);
    }
    
    @Test
    public void testPublishOverFlow() {
        TestEvent testEvent = new TestEvent();
        for (int i = 0; i < Byte.SIZE; i++) {
            namingEventPublisher.publish(testEvent);
        }
        namingEventPublisher.addSubscriber(subscriber, TestEvent.class);
        namingEventPublisher.publish(testEvent);
        verify(subscriber).onEvent(testEvent);
    }
    
    @Test(expected = IllegalStateException.class)
    public void testPublishAfterShutDown() throws NacosException {
        namingEventPublisher.shutdown();
        namingEventPublisher.publish(new TestEvent());
    }
    
    @Test
    public void getStatus() throws NacosException {
        namingEventPublisher.publish(new TestEvent());
        namingEventPublisher.publish(new TestEvent.TestEvent1());
        namingEventPublisher.publish(new TestEvent.TestEvent2());
        String expectedStatus = "Publisher TestEvent                     : shutdown=false, queue=      3/8      ";
        assertThat(namingEventPublisher.getStatus(), is(expectedStatus));
        namingEventPublisher.addSubscriber(subscriber, TestEvent.TestEvent1.class);
        ThreadUtils.sleep(2000L);
        expectedStatus = "Publisher TestEvent                     : shutdown=false, queue=      0/8      ";
        assertThat(namingEventPublisher.getStatus(), is(expectedStatus));
        namingEventPublisher.shutdown();
        expectedStatus = "Publisher TestEvent                     : shutdown= true, queue=      0/8      ";
        assertThat(namingEventPublisher.getStatus(), is(expectedStatus));
    }
    
}
