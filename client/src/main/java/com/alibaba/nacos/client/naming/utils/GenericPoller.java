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

package com.alibaba.nacos.client.naming.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generic Poller.
 *
 * @author nkorange
 */
public class GenericPoller<T> implements Poller<T> {
    
    private final AtomicInteger index = new AtomicInteger(0);
    
    private List<T> items = new ArrayList<T>();
    
    public GenericPoller(List<T> items) {
        this.items = items;
    }
    
    @Override
    public T next() {
        return items.get(Math.abs(index.getAndIncrement() % items.size()));
    }
    
    @Override
    public Poller<T> refresh(List<T> items) {
        return new GenericPoller<T>(items);
    }
}
