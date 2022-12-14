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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chooser.
 *
 * @author alibaba
 */
public class Chooser<K, T> {
    
    private final K uniqueKey;
    
    private volatile Ref<T> ref;
    
    /**
     * Random get one item.
     *
     * @return item
     */
    public T random() {
        List<T> items = ref.items;
        if (items.size() == 0) {
            return null;
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        return items.get(ThreadLocalRandom.current().nextInt(items.size()));
    }
    
    /**
     * Random get one item with weight.
     *
     * @return item
     */
    public T randomWithWeight() {
        Ref<T> ref = this.ref;
        double random = ThreadLocalRandom.current().nextDouble(0, 1);
        int index = Arrays.binarySearch(ref.weights, random);
        if (index < 0) {
            index = -index - 1;
        } else {
            return ref.items.get(index);
        }
        
        if (index < ref.weights.length) {
            if (random < ref.weights[index]) {
                return ref.items.get(index);
            }
        }
        
        if (ref.weights.length == 0) {
            throw new IllegalStateException("Cumulative Weight wrong , the array length is equal to 0.");
        }
        
        /* This should never happen, but it ensures we will return a correct
         * object in case there is some floating point inequality problem
         * wrt the cumulative probabilities. */
        return ref.items.get(ref.items.size() - 1);
    }
    
    public Chooser(K uniqueKey) {
        this(uniqueKey, new ArrayList<Pair<T>>());
    }
    
    public Chooser(K uniqueKey, List<Pair<T>> pairs) {
        Ref<T> ref = new Ref<>(pairs);
        ref.refresh();
        this.uniqueKey = uniqueKey;
        this.ref = ref;
    }
    
    public K getUniqueKey() {
        return uniqueKey;
    }
    
    public Ref<T> getRef() {
        return ref;
    }
    
    /**
     * refresh items.
     *
     * @param itemsWithWeight items with weight
     */
    public void refresh(List<Pair<T>> itemsWithWeight) {
        Ref<T> newRef = new Ref<>(itemsWithWeight);
        newRef.refresh();
        newRef.poller = this.ref.poller.refresh(newRef.items);
        this.ref = newRef;
    }
    
    public class Ref<T> {
        
        private List<Pair<T>> itemsWithWeight = new ArrayList<>();
        
        private final List<T> items = new ArrayList<>();
        
        private Poller<T> poller = new GenericPoller<>(items);
        
        private double[] weights;
        
        public Ref(List<Pair<T>> itemsWithWeight) {
            if (itemsWithWeight != null) {
                this.itemsWithWeight = itemsWithWeight;
            }
        }
        
        /**
         * Refresh.
         */
        public void refresh() {
            Double originWeightSum = (double) 0;
            int size = 0;
            for (Pair<T> item : itemsWithWeight) {
                
                double weight = item.weight();
                //ignore item which weight is zero.see test_randomWithWeight_weight0 in ChooserTest
                if (weight <= 0) {
                    continue;
                }
                
                items.add(item.item());
                if (Double.isInfinite(weight)) {
                    weight = 10000.0D;
                }
                if (Double.isNaN(weight)) {
                    weight = 1.0D;
                }
                originWeightSum += weight;
                size++;
            }
            
            weights = new double[size];
            double exactWeight;
            double randomRange = 0D;
            int index = 0;
            for (Pair<T> item : itemsWithWeight) {
                double singleWeight = item.weight();
                //ignore item which weight is zero.see test_randomWithWeight_weight0 in ChooserTest
                if (singleWeight <= 0) {
                    continue;
                }
                
                exactWeight = singleWeight / originWeightSum;
                weights[index] = randomRange + exactWeight;
                randomRange = weights[index++];
            }
            
            double doublePrecisionDelta = 0.0001;
            
            if (index == 0 || (Math.abs(weights[index - 1] - 1) < doublePrecisionDelta)) {
                return;
            }
            throw new IllegalStateException(
                    "Cumulative Weight calculate wrong , the sum of probabilities does not equals 1.");
        }
        
        @Override
        public int hashCode() {
            return itemsWithWeight.hashCode();
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null) {
                return false;
            }
            if (getClass() != other.getClass()) {
                return false;
            }
            if (!(other.getClass().getGenericInterfaces()[0].equals(this.getClass().getGenericInterfaces()[0]))) {
                return false;
            }
            Ref<T> otherRef = (Ref<T>) other;
            if (itemsWithWeight == null) {
                return otherRef.itemsWithWeight == null;
            } else {
                if (otherRef.itemsWithWeight == null) {
                    return false;
                } else {
                    return this.itemsWithWeight.equals(otherRef.itemsWithWeight);
                }
            }
        }
    }
    
    @Override
    public int hashCode() {
        return uniqueKey.hashCode();
    }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (getClass() != other.getClass()) {
            return false;
        }
        
        Chooser otherChooser = (Chooser) other;
        if (this.uniqueKey == null) {
            if (otherChooser.getUniqueKey() != null) {
                return false;
            }
        } else {
            if (otherChooser.getUniqueKey() == null) {
                return false;
            } else if (!this.uniqueKey.equals(otherChooser.getUniqueKey())) {
                return false;
            }
            
        }
        
        if (this.ref == null) {
            return otherChooser.getRef() == null;
        } else {
            if (otherChooser.getRef() == null) {
                return false;
            } else {
                return this.ref.equals(otherChooser.getRef());
            }
        }
    }
}
