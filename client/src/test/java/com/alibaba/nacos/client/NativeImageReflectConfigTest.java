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

package com.alibaba.nacos.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class NativeImageReflectConfigTest {
    
    private static final String REFLECT_CONFIG_PATH =
        "META-INF/native-image/com.alibaba.nacos/nacos-client/reflect-config.json";
    
    private JsonNode reflectConfig;
    
    @BeforeEach
    void setUp() throws IOException {
        InputStream inputStream =
            getClass().getClassLoader().getResourceAsStream(REFLECT_CONFIG_PATH);
        assertNotNull(inputStream, REFLECT_CONFIG_PATH + " must be available");
        try (InputStream configInputStream = inputStream) {
            reflectConfig = new ObjectMapper().readTree(configInputStream);
        }
        assertTrue(reflectConfig.isArray());
    }
    
    @Test
    void testShadedProtobufMethodParameters() {
        String byteString = "com.alibaba.nacos.shaded.com.google.protobuf.ByteString";
        
        assertMethod("com.alibaba.nacos.api.grpc.auto.Metadata$Builder", "setClientIpBytes",
            byteString);
        assertMethod("com.alibaba.nacos.api.grpc.auto.Metadata$Builder", "setTypeBytes",
            byteString);
        assertMethod("com.alibaba.nacos.api.grpc.auto.Payload$Builder", "setBody",
            "com.alibaba.nacos.shaded.com.google.protobuf.Any");
        assertMethodNotRegistered("com.alibaba.nacos.api.grpc.auto.Metadata$Builder",
            "setClientIpBytes",
            "com.google.protobuf.ByteString");
        assertMethodNotRegistered("com.alibaba.nacos.api.grpc.auto.Payload$Builder", "setBody",
            "com.google.protobuf.Any");
    }
    
    @Test
    void testAbilityNegotiationMethods() {
        String className = "com.alibaba.nacos.api.remote.request.SetupAckRequest";
        
        assertMethod(className, "<init>");
        assertMethod(className, "setAbilityTable", "java.util.Map");
    }
    
    @Test
    void testShadedGrpcRuntimeClasses() {
        assertClassMetadata("com.alibaba.nacos.shaded.io.grpc.census.InternalCensusStatsAccessor");
        assertClassMetadata(
            "com.alibaba.nacos.shaded.io.grpc.census.InternalCensusTracingAccessor");
        assertClassMetadata(
            "com.alibaba.nacos.shaded.io.grpc.internal.JndiResourceResolverFactory");
        assertClassMetadata("com.alibaba.nacos.shaded.io.grpc.netty.NettyChannelProvider");
        assertClassMetadata("com.alibaba.nacos.shaded.io.grpc.netty.UdsNettyChannelProvider");
        assertClassMetadata("com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.grpc.netty"
            + ".ProtocolNegotiators$PlaintextHandler");
        assertClassMetadata("com.alibaba.nacos.shaded.io.grpc.okhttp.OkHttpChannelProvider");
        assertClassMetadata("com.alibaba.nacos.shaded.io.grpc.override.ContextStorageOverride");
        assertClassMetadata(
            "com.alibaba.nacos.shaded.io.perfmark.impl.SecretPerfMarkImpl$PerfMarkImpl");
    }
    
    @Test
    void testShadedNettyRuntimeFields() {
        String channelPackage = "com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.";
        String utilPackage = "com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.";
        
        assertFields("com.alibaba.nacos.shaded.io.grpc.internal.SerializingExecutor", "runState");
        assertFields(channelPackage + "AbstractChannelHandlerContext", "handlerState");
        assertFields(channelPackage + "ChannelOutboundBuffer", "totalPendingSize", "unwritable");
        assertFields(channelPackage + "DefaultChannelConfig", "autoRead", "writeBufferWaterMark");
        assertFields(channelPackage + "DefaultChannelPipeline", "estimatorHandle");
        assertFields(utilPackage + "DefaultAttributeMap", "attributes");
        assertFields(utilPackage + "Recycler$DefaultHandle", "state");
        assertFields(utilPackage + "ResourceLeakDetector$DefaultResourceLeak", "droppedRecords",
            "head");
        assertFields(utilPackage + "concurrent.DefaultPromise", "result");
        assertFields(utilPackage + "concurrent.SingleThreadEventExecutor", "state",
            "threadProperties");
    }
    
    @Test
    void testShadedNettyQueueFields() {
        String queuePackage =
            "com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal.shaded.org"
                + ".jctools.queues.unpadded.";
        
        assertFields(queuePackage + "MpscUnpaddedArrayQueueConsumerIndexField", "consumerIndex");
        assertFields(queuePackage + "MpscUnpaddedArrayQueueProducerIndexField", "producerIndex");
        assertFields(queuePackage + "MpscUnpaddedArrayQueueProducerLimitField", "producerLimit");
        assertClassMetadata("com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.epoll"
            + ".AbstractEpollChannel");
    }
    
    private void assertClassMetadata(String className) {
        findClassMetadata(className);
    }
    
    private void assertFields(String className, String... fieldNames) {
        JsonNode fields = findClassMetadata(className).path("fields");
        for (String fieldName : fieldNames) {
            boolean fieldPresent = false;
            for (JsonNode field : fields) {
                fieldPresent |= fieldName.equals(field.path("name").asText());
            }
            assertTrue(fieldPresent, () -> className + " must register field " + fieldName);
        }
    }
    
    private void assertMethod(String className, String methodName, String... parameterTypes) {
        JsonNode methods = findClassMetadata(className).path("methods");
        for (JsonNode method : methods) {
            if (methodName.equals(method.path("name").asText())
                && parametersMatch(method, parameterTypes)) {
                return;
            }
        }
        fail(className + " must register method " + methodName);
    }
    
    private void assertMethodNotRegistered(String className, String methodName,
        String... parameterTypes) {
        JsonNode methods = findClassMetadata(className).path("methods");
        for (JsonNode method : methods) {
            boolean matches = methodName.equals(method.path("name").asText())
                && parametersMatch(method, parameterTypes);
            assertTrue(!matches,
                () -> className + " must not register unshaded method " + methodName);
        }
    }
    
    private boolean parametersMatch(JsonNode method, String... parameterTypes) {
        JsonNode parameters = method.path("parameterTypes");
        if (parameters.size() != parameterTypes.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!parameterTypes[i].equals(parameters.get(i).asText())) {
                return false;
            }
        }
        return true;
    }
    
    private JsonNode findClassMetadata(String className) {
        JsonNode result = null;
        for (JsonNode classMetadata : reflectConfig) {
            if (className.equals(classMetadata.path("name").asText())) {
                if (result != null) {
                    return fail(className + " must have exactly one metadata entry");
                }
                result = classMetadata;
            }
        }
        return result == null ? fail(className + " must have a metadata entry") : result;
    }
}
