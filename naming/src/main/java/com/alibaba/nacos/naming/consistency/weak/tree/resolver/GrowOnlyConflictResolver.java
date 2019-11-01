/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.weak.tree.resolver;

import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Instances;

import java.util.List;

/**
 * A simple grow-only conflict resolver.
 * For concurrent updates which can not be ordered, it calculates the union
 *
 * @author lostcharlie
 */
public class GrowOnlyConflictResolver implements StateBasedConflictResolver {
    private long maxTimeDifference;

    public long getMaxTimeDifference() {
        return maxTimeDifference;
    }

    private void setMaxTimeDifference(long maxTimeDifference) {
        this.maxTimeDifference = maxTimeDifference;
    }

    public GrowOnlyConflictResolver(long maxTimeDifference) {
        this.setMaxTimeDifference(maxTimeDifference);
    }

    @Override
    public void merge(Datum current, Datum target) {
        if (!(current.value instanceof Instances)) {
            // Only merge instances now
            return;
        }
        long timeDifference = target.timestamp.get() - current.timestamp.get();
        if (timeDifference < 0 && (-timeDifference) > this.getMaxTimeDifference()) {
            // Received obsoleted data, discard
            return;
        }
        if (timeDifference > 0 && timeDifference > this.getMaxTimeDifference()) {
            // Accept
            current.value = target.value;
            current.timestamp.set(target.timestamp.get());
        } else {
            // Resolve conflict. Merge two instance lists
            Instances instances = (Instances) current.value;
            Instances toMerge = (Instances) target.value;
            List<Instance> toMergeList = toMerge.getInstanceList();
            for (Instance instance : toMergeList) {
                if (!instances.getInstanceList().contains(instance)) {
                    instances.getInstanceList().add(instance);
                }
            }
            if (timeDifference > 0) {
                current.timestamp.set(target.timestamp.get());
            }
        }
    }
}
