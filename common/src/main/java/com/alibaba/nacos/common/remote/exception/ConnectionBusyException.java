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
 * connection is busy exception.
 *
 * @author liuzunfei
 * @version $Id: ConnectionBusyException.java, v 0.1 2020年11月30日 7:28 PM liuzunfei Exp $
 */
public class ConnectionBusyException extends RemoteException {
    
    private static final int CONNECTION_BUSY = 601;
    
    public ConnectionBusyException(String msg) {
        super(CONNECTION_BUSY, msg);
    }
    
    public ConnectionBusyException(Throwable throwable) {
        super(CONNECTION_BUSY, throwable);
    }
}
