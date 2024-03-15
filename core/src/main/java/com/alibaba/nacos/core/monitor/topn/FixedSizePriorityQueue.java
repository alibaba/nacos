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

package com.alibaba.nacos.core.monitor.topn;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Fixed size priority queue.
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.UndefineMagicConstantRule")
public class FixedSizePriorityQueue<T> {
    
    private Object[] elements;
    
    private int size;
    
    private Comparator<T> comparator;
    
    public FixedSizePriorityQueue(int capacity, Comparator<T> comparator) {
        elements = new Object[capacity];
        size = 0;
        this.comparator = comparator;
    }
    
    /**
     * Offer queue, if queue is full and offer element is not bigger than the first element in queue, offer element will
     * be ignored.
     *
     * @param element new element.
     */
    public void offer(T element) {
        if (size == elements.length) {
            if (comparator.compare(element, (T) elements[0]) > 0) {
                elements[0] = element;
                siftDown();
            }
        } else {
            elements[size] = element;
            siftUp(size);
            size++;
        }
    }
    
    private void siftUp(int index) {
        while (index > 0) {
            int parentIndex = (index - 1) / 2;
            if (comparator.compare((T) elements[index], (T) elements[parentIndex]) > 0) {
                break;
            }
            swap(index, parentIndex);
            index = parentIndex;
        }
    }
    
    private void siftDown() {
        int index = 0;
        while (index * 2 + 1 < size) {
            int leftChild = index * 2 + 1;
            int rightChild = index * 2 + 2;
            int minChildIndex = leftChild;
            if (rightChild < size && comparator.compare((T) elements[rightChild], (T) elements[leftChild]) < 0) {
                minChildIndex = rightChild;
            }
            if (comparator.compare((T) elements[index], (T) elements[minChildIndex]) < 0) {
                break;
            }
            swap(index, minChildIndex);
            index = minChildIndex;
        }
    }
    
    private void swap(int i, int j) {
        Object temp = elements[i];
        elements[i] = elements[j];
        elements[j] = temp;
    }
    
    /**
     * Transfer queue to list without order.
     *
     * @return list
     */
    public List<T> toList() {
        List<T> result = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            result.add((T) elements[i]);
        }
        return result;
    }
}
