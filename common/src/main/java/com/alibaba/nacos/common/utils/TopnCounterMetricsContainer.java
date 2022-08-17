/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * container for top n counter metrics, increment and remove cost O(1) time.
 *
 * @author <a href="mailto:liuyixiao0821@gmail.com">liuyixiao</a>
 */
public class TopnCounterMetricsContainer {
    
    /**
     * dataId -> count.
     */
    private ConcurrentHashMap<String, AtomicInteger> dataCount;
    
    /**
     * count -> node.
     */
    private ConcurrentHashMap<Integer, DoublyLinkedNode> specifiedCountDataIdSets;
    
    private DoublyLinkedNode dummyHead;
    
    public TopnCounterMetricsContainer() {
        dataCount = new ConcurrentHashMap<>();
        specifiedCountDataIdSets = new ConcurrentHashMap<>();
        dummyHead = new DoublyLinkedNode(null, null, null, -1);
        dummyHead.next = new DoublyLinkedNode(null, dummyHead, new ConcurrentHashSet<>(), 0);
        specifiedCountDataIdSets.put(0, dummyHead.next);
    }
    
    public List<Pair<String, AtomicInteger>> getTopNCounter(int n) {
        List<Pair<String, AtomicInteger>> topnCounter = new LinkedList<>();
        DoublyLinkedNode curr = dummyHead;
        while (curr.next != null && topnCounter.size() < n) {
            for (String dataId : curr.next.dataSet) {
                // use inner AtomicInteger to reflect change to prometheus
                topnCounter.add(new Pair<>(dataId, dataCount.get(dataId)));
                if (topnCounter.size() == n) {
                    break;
                }
            }
            curr = curr.next;
        }
        return topnCounter;
    }
    
    /**
     * put(String dataId, 0).
     *
     * @param dataId data name or data key.
     */
    public void put(String dataId) {
        put(dataId, 0);
    }
    
    /**
     * put new data into container, if already exist, update it.
     * this method could be slow (O(N)), most time use increment.
     *
     * @param dataId data name or data key.
     * @param count data count.
     */
    public void put(String dataId, int count) {
        if (dataCount.containsKey(dataId)) {
            removeFromSpecifiedCountDataIdSets(dataId);
            dataCount.get(dataId).set(count);
        } else {
            dataCount.put(dataId, new AtomicInteger(count));
        }
        insertIntoSpecifiedCountDataIdSets(dataId, count);
    }
    
    /**
     * get data count by dataId.
     *
     * @param dataId data name or data key.
     * @return data count or -1 if not exist.
     */
    public int get(String dataId) {
        if (dataCount.containsKey(dataId)) {
            return dataCount.get(dataId).get();
        }
        return -1;
    }
    
    /**
     * increment the count of dataId.
     *
     * @param dataId data name or data key.
     */
    public void increment(String dataId) {
        if (!dataCount.containsKey(dataId)) {
            put(dataId);
        }
        DoublyLinkedNode prev = removeFromSpecifiedCountDataIdSets(dataId);
        int newCount = dataCount.get(dataId).incrementAndGet();
        if (!isDummyHead(prev) && prev.count == newCount) {
            insertIntoSpecifiedCountDataIdSets(dataId, prev);
        } else {
            // prev.count > newCount
            DoublyLinkedNode newNode = new DoublyLinkedNode(prev.next, prev, new ConcurrentHashSet<>(), newCount);
            if (prev.next != null) {
                prev.next.prev = newNode;
            }
            prev.next = newNode;
            newNode.dataSet.add(dataId);
            specifiedCountDataIdSets.put(newCount, newNode);
        }
    }
    
    /**
     * remove data.
     *
     * @param dataId data name or data key.
     * @return data count or null if data is not exist.
     */
    public AtomicInteger remove(String dataId) {
        if (dataCount.containsKey(dataId)) {
            removeFromSpecifiedCountDataIdSets(dataId);
            return dataCount.remove(dataId);
        }
        return null;
    }
    
    /**
     * remove all data.
     */
    public void removeAll() {
        for (String dataId : dataCount.keySet()) {
            removeFromSpecifiedCountDataIdSets(dataId);
        }
        dataCount.clear();
    }
    
    private DoublyLinkedNode removeFromSpecifiedCountDataIdSets(String dataId) {
        int count = dataCount.get(dataId).get();
        DoublyLinkedNode node = specifiedCountDataIdSets.get(count);
        node.dataSet.remove(dataId);
        // keep the 0 count node.
        if (node.dataSet.size() == 0 && node.count != 0) {
            node.prev.next = node.next;
            if (node.next != null) {
                node.next.prev = node.prev;
            }
            specifiedCountDataIdSets.remove(node.count);
        }
        return node.prev;
    }
    
    private void insertIntoSpecifiedCountDataIdSets(String dataId, int count) {
        if (specifiedCountDataIdSets.containsKey(count)) {
            specifiedCountDataIdSets.get(count).dataSet.add(dataId);
        } else {
            DoublyLinkedNode prev = dummyHead;
            while (prev.next != null) {
                if (prev.next.count < count) {
                    break;
                } else {
                    prev = prev.next;
                }
            }
            DoublyLinkedNode newNode = new DoublyLinkedNode(prev.next, prev, new ConcurrentHashSet<>(), count);
            if (prev.next != null) {
                prev.next.prev = newNode;
            }
            prev.next = newNode;
            newNode.dataSet.add(dataId);
            specifiedCountDataIdSets.put(count, newNode);
        }
    }
    
    private void insertIntoSpecifiedCountDataIdSets(String dataId, DoublyLinkedNode targetSet) {
        targetSet.dataSet.add(dataId);
    }
    
    private boolean isDummyHead(DoublyLinkedNode node) {
        return node.count == -1;
    }
    
    private class DoublyLinkedNode {
        
        public DoublyLinkedNode next;
        
        public DoublyLinkedNode prev;
        
        public ConcurrentHashSet<String> dataSet;
        
        public int count;
        
        public DoublyLinkedNode(DoublyLinkedNode next, DoublyLinkedNode prev, ConcurrentHashSet<String> dataSet, int count) {
            this.next = next;
            this.prev = prev;
            this.dataSet = dataSet;
            this.count = count;
        }
    }
}
