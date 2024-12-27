/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ObservableTest {
    
    @Mock
    private Observer observer;
    
    private Observable observable;
    
    @BeforeEach
    void setUp() throws Exception {
        observable = new Observable();
    }
    
    @AfterEach
    void tearDown() throws Exception {
    }
    
    @Test
    void testAddObserver() {
        observable.addObserver(observer);
        assertEquals(1, observable.countObservers());
        verify(observer).update(observable);
    }
    
    @Test
    void testDeleteObserver() {
        observable.addObserver(observer);
        assertEquals(1, observable.countObservers());
        observable.deleteObserver(observer);
        assertEquals(0, observable.countObservers());
    }
    
    @Test
    void testNotifyObservers() {
        observable.addObserver(observer);
        reset(observer);
        observable.notifyObservers();
        assertFalse(observable.hasChanged());
        verify(observer, never()).update(observable);
        observable.setChanged();
        assertTrue(observable.hasChanged());
        observable.notifyObservers();
        verify(observer).update(observable);
        assertFalse(observable.hasChanged());
    }
    
    @Test
    void testDeleteObservers() {
        observable.addObserver(observer);
        observable.deleteObservers();
        assertEquals(1, observable.countObservers());
    }
}