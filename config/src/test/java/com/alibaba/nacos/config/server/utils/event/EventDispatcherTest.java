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
package com.alibaba.nacos.config.server.utils.event;

import com.alibaba.nacos.config.server.utils.event.EventDispatcher.AbstractEventListener;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher.Event;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public class EventDispatcherTest {

    @After
    public void after() {
        EventDispatcher.clear();
    }

    @Ignore
    @Test
    public void testAddListener() throws Exception {
        final AbstractEventListener listener = new MockListener();

        int vusers = 1000;
        final CountDownLatch latch = new CountDownLatch(vusers);

        for (int i = 0; i < vusers; ++i) {
            new Thread(new Runnable() {
                public void run() {
                    latch.countDown();
                    EventDispatcher.addEventListener(listener);
                }
            }).start();
        }

        latch.await();
        assertEquals(1, EventDispatcher.LISTENER_HUB.size());
    }

    @Test
    public void testFireEvent() {
        EventDispatcher.fireEvent(new MockEvent());
        assertEquals(0, MockListener.count);

        EventDispatcher.addEventListener(new MockListener());

        EventDispatcher.fireEvent(new MockEvent());
        assertEquals(1, MockListener.count);

        EventDispatcher.fireEvent(new MockEvent());
        assertEquals(2, MockListener.count);
    }
}

class MockEvent implements Event {
}

class MockListener extends AbstractEventListener {
    static int count = 0;

    @Override
    public List<Class<? extends Event>> interest() {
        List<Class<? extends Event>> types = new ArrayList<Class<? extends Event>>();
        types.add(MockEvent.class);
        return types;
    }

    @Override
    public void onEvent(Event event) {
        ++count;
    }
}
