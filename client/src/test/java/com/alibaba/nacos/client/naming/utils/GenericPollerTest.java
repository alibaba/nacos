/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.naming.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenericPollerTest {

    @Test
    void testNext() {
        String item1 = "item1";
        String item2 = "item2";
        GenericPoller<String> poller = new GenericPoller<>(Arrays.asList(item1, item2));
        assertEquals(item1, poller.next());
        assertEquals(item2, poller.next());
        assertEquals(item1, poller.next());
    }

    @Test
    void testRefresh() {
        String item1 = "item1";
        String item2 = "item2";
        GenericPoller<String> poller = new GenericPoller<>(Arrays.asList(item1, item2));
        Poller<String> poller1 = poller.refresh(Arrays.asList(item2));
        assertEquals(item2, poller1.next());
    }
}