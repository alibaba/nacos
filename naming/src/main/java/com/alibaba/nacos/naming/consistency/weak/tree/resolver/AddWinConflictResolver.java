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
import com.alibaba.nacos.naming.consistency.weak.Operation;
import com.alibaba.nacos.naming.consistency.weak.OperationType;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Instances;

import java.util.List;

/**
 * An Add-win resolver which guarantee the "Add win" semantics.
 * Currently, it solves conflicts by calculating union of two sets, and discards removal operations.
 * Still needs to be improved for a production-ready CRDT (Conflict-free Replicated Data Type).
 *
 * @author lostcharlie
 */
public class AddWinConflictResolver implements OperationBasedConflictResolver {
    private long maxTimeDifference;

    public long getMaxTimeDifference() {
        return maxTimeDifference;
    }

    private void setMaxTimeDifference(long maxTimeDifference) {
        this.maxTimeDifference = maxTimeDifference;
    }

    public AddWinConflictResolver(long maxTimeDifference) {
        this.setMaxTimeDifference(maxTimeDifference);
    }

    @Override
    public void merge(Datum current, Operation toApply) {
        if (!(current.value instanceof Instances)) {
            // Only merge instances now
            return;
        }
        long timeDifference = toApply.getTimestamp().get() - current.timestamp.get();
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
                    instances.getInstanceList().remove(toRemove);
                }
            }
        } else {
            // Resolve conflict
            Instances instances = (Instances) current.value;
            if (toApply.getOperationType() == OperationType.ADD_INSTANCE) {
                this.doAddInstances(toApply, instances);
            }
            // Discard conflict removal operation
        }
        if (timeDifference > 0) {
            current.timestamp.set(toApply.getTimestamp().get());
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
