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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

/** Connection evidence for safe AUTO read fallback. */
final class AiTransportExceptionUtils {
    
    private AiTransportExceptionUtils() {
    }
    
    static boolean isConnectionFailure(Exception exception) {
        int code = exception instanceof NacosException ? ((NacosException) exception).getErrCode()
            : ((NacosRuntimeException) exception).getErrCode();
        if (code == NacosException.CLIENT_DISCONNECT || code == NacosException.UN_REGISTER) {
            return true;
        }
        // Only generic transport wrappers can be unwrapped. Business errors win even if
        // the connection disappears at the same time or a cause contains UNAVAILABLE.
        if (code != NacosException.SERVER_ERROR && code != NacosException.CLIENT_ERROR) {
            return false;
        }
        Throwable current = exception;
        while (current != null) {
            if (current instanceof NacosException) {
                int causeCode = ((NacosException) current).getErrCode();
                if (causeCode == NacosException.CLIENT_DISCONNECT
                    || causeCode == NacosException.UN_REGISTER) {
                    return true;
                }
                if (causeCode != NacosException.SERVER_ERROR
                    && causeCode != NacosException.CLIENT_ERROR) {
                    return false;
                }
            }
            if (current instanceof StatusRuntimeException) {
                return ((StatusRuntimeException) current).getStatus()
                    .getCode() == Status.Code.UNAVAILABLE;
            }
            if (current instanceof StatusException) {
                return ((StatusException) current).getStatus().getCode() == Status.Code.UNAVAILABLE;
            }
            current = current.getCause();
        }
        return false;
    }
}
