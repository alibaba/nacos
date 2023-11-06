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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ObservableTest {
    
    @Mock
    private Observer observer;
    
    private Observable observable;
    
    @Before
    public void setUp() throws Exception {
        observable = new Observable();
    }
    
    @After
    public void tearDown() throws Exception {
    }
    
    @Test
    public void testAddObserver() {
        observable.addObserver(observer);
        assertEquals(1, observable.countObservers());
        verify(observer).update(observable);
    }
    
    @Test
    public void testDeleteObserver() {
        observable.addObserver(observer);
        assertEquals(1, observable.countObservers());
        observable.deleteObserver(observer);
        assertEquals(0, observable.countObservers());
    }
    
    @Test
    public void testNotifyObservers() {
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
    public void testDeleteObservers() {
        observable.addObserver(observer);
        observable.deleteObservers();
        assertEquals(1, observable.countObservers());
    }
}