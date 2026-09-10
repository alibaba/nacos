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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiTransportExceptionUtilsTest {
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("connectionEvidence")
    void fallbackRequiresConnectionEvidenceWithoutBusinessFailure(String scenario,
        Exception exception, boolean expected) {
        assertEquals(expected, AiTransportExceptionUtils.isConnectionFailure(exception), scenario);
    }
    
    static Stream<Arguments> connectionEvidence() {
        return Stream.of(
            Arguments.of("checked disconnected",
                new NacosException(NacosException.CLIENT_DISCONNECT, "lost"), true),
            Arguments.of("checked unregistered",
                new NacosException(NacosException.UN_REGISTER, "lost"), true),
            Arguments.of("runtime disconnected",
                new NacosRuntimeException(NacosException.CLIENT_DISCONNECT), true),
            Arguments.of("runtime unregistered",
                new NacosRuntimeException(NacosException.UN_REGISTER), true),
            Arguments.of("server wrapper with checked unavailable",
                new NacosException(NacosException.SERVER_ERROR, Status.UNAVAILABLE.asException()),
                true),
            Arguments.of("client wrapper with runtime unavailable",
                new NacosRuntimeException(NacosException.CLIENT_ERROR,
                    Status.UNAVAILABLE.asRuntimeException()),
                true),
            Arguments.of("nested disconnected", new NacosException(NacosException.SERVER_ERROR,
                new IOException(new NacosException(NacosException.CLIENT_DISCONNECT, "lost"))),
                true),
            Arguments.of("nested unregistered",
                new NacosRuntimeException(NacosException.CLIENT_ERROR,
                    new NacosException(NacosException.UN_REGISTER, "lost")),
                true),
            Arguments.of("nested generic client wrapper",
                new NacosException(NacosException.SERVER_ERROR,
                    new NacosException(NacosException.CLIENT_ERROR,
                        Status.UNAVAILABLE.asException())),
                true),
            Arguments.of("nested generic server wrapper",
                new NacosRuntimeException(NacosException.CLIENT_ERROR,
                    new NacosException(NacosException.SERVER_ERROR,
                        Status.UNAVAILABLE.asRuntimeException())),
                true),
            Arguments.of("checked business code wins", new NacosException(NacosException.NO_RIGHT,
                Status.UNAVAILABLE.asRuntimeException()), false),
            Arguments.of("runtime business code wins",
                new NacosRuntimeException(NacosException.NOT_FOUND,
                    Status.UNAVAILABLE.asException()),
                false),
            Arguments.of("nested business code wins",
                new NacosException(NacosException.SERVER_ERROR,
                    new NacosException(NacosException.INVALID_PARAM,
                        Status.UNAVAILABLE.asException())),
                false),
            Arguments.of("checked deadline is not connection evidence",
                new NacosException(NacosException.SERVER_ERROR,
                    Status.DEADLINE_EXCEEDED.asException()),
                false),
            Arguments.of("runtime internal status is not connection evidence",
                new NacosException(NacosException.CLIENT_ERROR,
                    Status.INTERNAL.asRuntimeException()),
                false),
            Arguments.of("bare server wrapper",
                new NacosException(NacosException.SERVER_ERROR, "unknown"), false),
            Arguments.of("bare client wrapper",
                new NacosRuntimeException(NacosException.CLIENT_ERROR), false),
            Arguments.of("unrelated cause", new NacosException(NacosException.CLIENT_ERROR,
                new IOException("read failed")), false));
    }
}
