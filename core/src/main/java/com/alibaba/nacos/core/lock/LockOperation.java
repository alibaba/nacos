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

package com.alibaba.nacos.core.lock;

import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
enum LockOperation {

    /**
     * lock operation
     */
    LOCK("lock"),

    /**
     * unlock operation
     */
    UN_LOCK("unlock"),

    /**
     * lock renew
     */
    RE_NEW("renew")

    ;

    private String operation;

    LockOperation(String operation) {
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }

    public static LockOperation sourceOf(final String operation) {
        for (LockOperation lockOperation : LockOperation.values()) {
            if (Objects.equals(operation, lockOperation.getOperation())) {
                return lockOperation;
            }
        }
        return null;
    }

}
