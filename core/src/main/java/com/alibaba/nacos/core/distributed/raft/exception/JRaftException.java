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
 * Abnormal JRaft.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class JRaftException extends RuntimeException {
    
    private static final long serialVersionUID = 8802314713344513544L;
    
    public JRaftException() {
    }
    
    public JRaftException(String message) {
        super(message);
    }
    
    public JRaftException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public JRaftException(Throwable cause) {
        super(cause);
    }
    
    public JRaftException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
