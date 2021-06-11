/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.remote.exception;

/**
 * connection already closed exception.
 *
 * @author liuzunfei
 * @version $Id: ConnectionAlreadyClosedException.java, v 0.1 2020年07月22日 7:28 PM liuzunfei Exp $
 */
public class ConnectionAlreadyClosedException extends RemoteException {
    
    private static final int CONNECTION_ALREADY_CLOSED = 600;
    
    public ConnectionAlreadyClosedException(String msg) {
        super(CONNECTION_ALREADY_CLOSED);
    }
    
    public ConnectionAlreadyClosedException() {
        super(CONNECTION_ALREADY_CLOSED);
    }
    
    public ConnectionAlreadyClosedException(Throwable throwable) {
        super(CONNECTION_ALREADY_CLOSED, throwable);
    }
}
