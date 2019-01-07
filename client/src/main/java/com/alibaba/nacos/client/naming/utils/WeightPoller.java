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

/**
 *
 * Nginx Poll-With-Weight algorithm
 * Make the polling object more uniform
 * @author XCXCXCXCX
 */
public class WeightPoller<T> implements Poller<T> {

    private final List<T> items;

    private final double[] weights;

    private final double[] currentWeight;

    public WeightPoller(List<Pair<T>> itemsWithWeight) {
        int size = itemsWithWeight.size();
        List<T> items = new ArrayList<T>();
        double weights[] = new double[size];
        for(int i = 0; i < size; i++){
            Pair<T> pair = itemsWithWeight.get(i);
            items.add(pair.item());
            weights[i] = pair.weight();
        }
        this.items = items;
        this.weights = weights;
        this.currentWeight = weights.clone();
    }

    public WeightPoller(List<T> items, double[] weights) {
        this.items = items;
        this.weights = weights;
        currentWeight = weights.clone();
    }

    /**
     * Get next element selected by poller
     *
     * @return next element
     */
    @Override
    public T next() {
        try{
            return getMaxWeightItem();
        }finally {
            prepareForNext();
        }
    }

    private synchronized void prepareForNext() {
        synchronized (currentWeight){
            for (int i = 0; i < currentWeight.length; i++){
                currentWeight[i] += weights[i];
            }
        }
    }

    private T getMaxWeightItem() {
        int maxWeightIndex = 0;
        synchronized (currentWeight){
            double maxWeight = currentWeight[0];
            double totalWeight = 0D;
            for (int i = 0; i < currentWeight.length; i++){
                totalWeight += currentWeight[i];
                if(currentWeight[i] >  maxWeight){
                    maxWeightIndex = i;
                    maxWeight = currentWeight[i];
                }
            }
            currentWeight[maxWeightIndex] -= totalWeight;
        }
        return items.get(maxWeightIndex);
    }

    /**
     * not implement
     * not allow to refresh, you can create new WeightPoller instance if you have to use refresh.
     *
     * @param items new item list
     * @return new poller instance
     */
    @Override
    public Poller<T> refresh(List<T> items) {
        return null;
    }
}
