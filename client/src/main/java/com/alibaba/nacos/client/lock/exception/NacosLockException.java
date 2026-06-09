/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.lock.exception;

/**
 * Exception thrown by Nacos distributed lock operations.
 *
 * <p>This exception indicates a failure in lock acquisition, release,
 * or other lock-related operations. It extends {@link RuntimeException}
 * to comply with the JUC {@link java.util.concurrent.locks.Lock} interface
 * contract which does not allow checked exceptions.
 *
 * @author DHX
 * @date 2026/05/31
 */
public class NacosLockException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    public NacosLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
