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

package com.alibaba.nacos.client.selector;

import com.alibaba.nacos.client.naming.selector.NamingSelectorWrapper;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class SelectorManagerTest {
    
    @Test
    public void testCurd() {
        SelectorManager<NamingSelectorWrapper> selectorManager = new SelectorManager<>();
        String subId = "subId";
        NamingSelectorWrapper sw = mock(NamingSelectorWrapper.class);
        selectorManager.addSelectorWrapper(subId, sw);
        assertTrue(selectorManager.getSelectorWrappers(subId).contains(sw));
        selectorManager.removeSelectorWrapper(subId, sw);
        assertTrue(selectorManager.getSelectorWrappers(subId).isEmpty());
    }
    
    @Test
    public void testSubInfo() {
        SelectorManager<NamingSelectorWrapper> selectorManager = new SelectorManager<>();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            list.add(generateRandomString(2, 32));
        }
        
        for (String subId : list) {
            selectorManager.addSelectorWrapper(subId, mock(NamingSelectorWrapper.class));
            assertTrue(selectorManager.isSubscribed(subId));
        }
        
        Set<String> subsSet = selectorManager.getSubscriptions();
        for (String subId : subsSet) {
            assertTrue(list.contains(subId));
        }
        
        for (String subId : list) {
            selectorManager.removeSubscription(subId);
            assertFalse(selectorManager.isSubscribed(subId));
        }
    }
    
    private static String generateRandomString(int minLength, int maxLength) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        
        Random random = new Random();
        int length = random.nextInt(maxLength - minLength + 1) + minLength;
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(characters.length());
            char randomChar = characters.charAt(index);
            sb.append(randomChar);
        }
        
        return sb.toString();
    }
}
