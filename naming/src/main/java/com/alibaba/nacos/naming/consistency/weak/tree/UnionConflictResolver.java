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
package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.nacos.naming.consistency.ephemeral.simple.SimpleDatum;
import com.alibaba.nacos.naming.consistency.weak.Operation;
import com.alibaba.nacos.naming.consistency.weak.OperationType;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Instances;

import java.util.List;

/**
 * Union conflict resolver which calculates the union of concurrent "Add" operation.
 * Additionally, if there is a conflict between "Add" and "Remove", it accepts the "Add" operation.
 *
 * @author lostcharlie
 */
public class UnionConflictResolver implements ConflictResolver {
    private long maxTimeDifference;

    public long getMaxTimeDifference() {
        return maxTimeDifference;
    }

    private void setMaxTimeDifference(long maxTimeDifference) {
        this.maxTimeDifference = maxTimeDifference;
    }

    public UnionConflictResolver(long maxTimeDifference) {
        this.setMaxTimeDifference(maxTimeDifference);
    }

    @Override
    public void merge(SimpleDatum current, Operation toApply) {
        if (!(current.value instanceof Instances)) {
            // Only merge instances now
            return;
        }
        long timeDifference = toApply.getRealTime() - current.realTime;
        if (timeDifference < 0 && (-timeDifference) > this.getMaxTimeDifference()) {
            // Received obsoleted data, discard
            return;
        }
        if (timeDifference > 0 && timeDifference > this.getMaxTimeDifference()) {
            // Accept
            Instances instances = (Instances) current.value;
            if (toApply.getOperationType() == OperationType.ADD_INSTANCE) {
                this.doAddInstances(toApply, instances);
            } else if (toApply.getOperationType() == OperationType.REMOVE_INSTANCE) {
                List<Instance> toRemoveList = ((Instances) toApply.getTargetValue()).getInstanceList();
                for (Instance toRemove : toRemoveList) {
                    if (instances.getInstanceList().contains(toRemove)) {
                        instances.getInstanceList().remove(toRemove);
                    }
                }
            }
            current.timestamp.incrementAndGet();
            current.realTime = toApply.getRealTime();
            return;
        } else {
            // Resolve conflict
            Instances instances = (Instances) current.value;
            if (toApply.getOperationType() == OperationType.ADD_INSTANCE) {
                this.doAddInstances(toApply, instances);
            }
            // Discard conflict removal operation
        }
    }

    private void doAddInstances(Operation toApply, Instances instances) {
        List<Instance> toAddList = ((Instances) toApply.getTargetValue()).getInstanceList();
        for (Instance toAdd : toAddList) {
            if (!(instances.getInstanceList().contains(toAdd))) {
                instances.getInstanceList().add(toAdd);
            }
        }
    }
}
