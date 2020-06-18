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

package com.alibaba.nacos.core.distributed.raft.exception;

/**
 * This exception is thrown if the current Raft Group Cluster does not elect a leader.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NoLeaderException extends Exception {
    
    private static final long serialVersionUID = 1755681688785678765L;
    
    public NoLeaderException() {
    }
    
    public NoLeaderException(String group) {
        super("The Raft Group [" + group + "] did not find the Leader node");
    }
    
    public NoLeaderException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public NoLeaderException(Throwable cause) {
        super(cause);
    }
    
    public NoLeaderException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
