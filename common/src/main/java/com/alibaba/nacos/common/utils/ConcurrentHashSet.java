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

package com.alibaba.nacos.common.utils;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concurrent Hash Set implement by {@link ConcurrentHashMap}.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConcurrentHashSet<E> extends AbstractSet<E> {
    
    private ConcurrentHashMap<E, Boolean> map;
    
    public ConcurrentHashSet() {
        super();
        map = new ConcurrentHashMap<E, Boolean>();
    }
    
    @Override
    public int size() {
        return map.size();
    }
    
    @Override
    public boolean contains(Object o) {
        return map.containsKey(o);
    }
    
    @Override
    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }
    
    @Override
    public boolean add(E o) {
        return map.putIfAbsent(o, Boolean.TRUE) == null;
    }
    
    @Override
    public boolean remove(Object o) {
        return map.remove(o) != null;
    }
    
    @Override
    public void clear() {
        map.clear();
    }
}
