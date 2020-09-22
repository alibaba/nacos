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

package com.alibaba.nacos.api.grpc.auto;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;

/**
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.20.0)", comments = "Source: nacos_grpc_service.proto")
public final class BiRequestStreamGrpc {
    
    private BiRequestStreamGrpc() {
    }
    
    public static final String SERVICE_NAME = "BiRequestStream";
    
    // Static method descriptors that strictly reflect the proto.
    private static volatile io.grpc.MethodDescriptor<com.alibaba.nacos.api.grpc.auto.Payload, com.alibaba.nacos.api.grpc.auto.Payload> getRequestBiStreamMethod;
    
    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "requestBiStream", requestType = com.alibaba.nacos.api.grpc.auto.Payload.class, responseType = com.alibaba.nacos.api.grpc.auto.Payload.class, methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
    public static io.grpc.MethodDescriptor<com.alibaba.nacos.api.grpc.auto.Payload, com.alibaba.nacos.api.grpc.auto.Payload> getRequestBiStreamMethod() {
        io.grpc.MethodDescriptor<com.alibaba.nacos.api.grpc.auto.Payload, com.alibaba.nacos.api.grpc.auto.Payload> getRequestBiStreamMethod;
        if ((getRequestBiStreamMethod = BiRequestStreamGrpc.getRequestBiStreamMethod) == null) {
            synchronized (BiRequestStreamGrpc.class) {
                if ((getRequestBiStreamMethod = BiRequestStreamGrpc.getRequestBiStreamMethod) == null) {
                    BiRequestStreamGrpc.getRequestBiStreamMethod = getRequestBiStreamMethod = io.grpc.MethodDescriptor.<com.alibaba.nacos.api.grpc.auto.Payload, com.alibaba.nacos.api.grpc.auto.Payload>newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
                            .setFullMethodName(generateFullMethodName("BiRequestStream", "requestBiStream"))
                            .setSampledToLocalTracing(true).setRequestMarshaller(io.grpc.protobuf.ProtoUtils
                                    .marshaller(com.alibaba.nacos.api.grpc.auto.Payload.getDefaultInstance()))
                            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils
                                    .marshaller(com.alibaba.nacos.api.grpc.auto.Payload.getDefaultInstance()))
                            .setSchemaDescriptor(new BiRequestStreamMethodDescriptorSupplier("requestBiStream"))
                            .build();
                }
            }
        }
        return getRequestBiStreamMethod;
    }
    
    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static BiRequestStreamStub newStub(io.grpc.Channel channel) {
        return new BiRequestStreamStub(channel);
    }
    
    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static BiRequestStreamBlockingStub newBlockingStub(io.grpc.Channel channel) {
        return new BiRequestStreamBlockingStub(channel);
    }
    
    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static BiRequestStreamFutureStub newFutureStub(io.grpc.Channel channel) {
        return new BiRequestStreamFutureStub(channel);
    }
    
    /**
     */
    public static abstract class BiRequestStreamImplBase implements io.grpc.BindableService {
        
        /**
         * <pre>
         * Sends a commonRequest
         * </pre>
         */
        public io.grpc.stub.StreamObserver<com.alibaba.nacos.api.grpc.auto.Payload> requestBiStream(
                io.grpc.stub.StreamObserver<com.alibaba.nacos.api.grpc.auto.Payload> responseObserver) {
            return asyncUnimplementedStreamingCall(getRequestBiStreamMethod(), responseObserver);
        }
    
        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor()).addMethod(getRequestBiStreamMethod(),
                    asyncBidiStreamingCall(
                            new MethodHandlers<com.alibaba.nacos.api.grpc.auto.Payload, com.alibaba.nacos.api.grpc.auto.Payload>(
                                    this, METHODID_REQUEST_BI_STREAM))).build();
        }
    }
    
    /**
     */
    public static final class BiRequestStreamStub extends io.grpc.stub.AbstractStub<BiRequestStreamStub> {
        
        private BiRequestStreamStub(io.grpc.Channel channel) {
            super(channel);
        }
        
        private BiRequestStreamStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }
    
        @java.lang.Override
        protected BiRequestStreamStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new BiRequestStreamStub(channel, callOptions);
        }
        
        /**
         * <pre>
         * Sends a commonRequest
         * </pre>
         */
        public io.grpc.stub.StreamObserver<com.alibaba.nacos.api.grpc.auto.Payload> requestBiStream(
                io.grpc.stub.StreamObserver<com.alibaba.nacos.api.grpc.auto.Payload> responseObserver) {
            return asyncBidiStreamingCall(getChannel().newCall(getRequestBiStreamMethod(), getCallOptions()),
                    responseObserver);
        }
    }
    
    /**
     */
    public static final class BiRequestStreamBlockingStub
            extends io.grpc.stub.AbstractStub<BiRequestStreamBlockingStub> {
        
        private BiRequestStreamBlockingStub(io.grpc.Channel channel) {
            super(channel);
        }
        
        private BiRequestStreamBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }
    
        @java.lang.Override
        protected BiRequestStreamBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new BiRequestStreamBlockingStub(channel, callOptions);
        }
    }
    
    /**
     */
    public static final class BiRequestStreamFutureStub extends io.grpc.stub.AbstractStub<BiRequestStreamFutureStub> {
        
        private BiRequestStreamFutureStub(io.grpc.Channel channel) {
            super(channel);
        }
        
        private BiRequestStreamFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }
    
        @java.lang.Override
        protected BiRequestStreamFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new BiRequestStreamFutureStub(channel, callOptions);
        }
    }
    
    private static final int METHODID_REQUEST_BI_STREAM = 0;
    
    private static final class MethodHandlers<Req, Resp>
            implements io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>, io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>, io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>, io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
        
        private final BiRequestStreamImplBase serviceImpl;
        
        private final int methodId;
        
        MethodHandlers(BiRequestStreamImplBase serviceImpl, int methodId) {
            this.serviceImpl = serviceImpl;
            this.methodId = methodId;
        }
    
        @java.lang.Override
        @java.lang.SuppressWarnings("unchecked")
        public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
            switch (methodId) {
                default:
                    throw new AssertionError();
            }
        }
    
        @java.lang.Override
        @java.lang.SuppressWarnings("unchecked")
        public io.grpc.stub.StreamObserver<Req> invoke(io.grpc.stub.StreamObserver<Resp> responseObserver) {
            switch (methodId) {
                case METHODID_REQUEST_BI_STREAM:
                    return (io.grpc.stub.StreamObserver<Req>) serviceImpl.requestBiStream(
                            (io.grpc.stub.StreamObserver<com.alibaba.nacos.api.grpc.auto.Payload>) responseObserver);
                default:
                    throw new AssertionError();
            }
        }
    }
    
    private static abstract class BiRequestStreamBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
        
        BiRequestStreamBaseDescriptorSupplier() {
        }
    
        @java.lang.Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return com.alibaba.nacos.api.grpc.auto.NacosGrpcService.getDescriptor();
        }
    
        @java.lang.Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("BiRequestStream");
        }
    }
    
    private static final class BiRequestStreamFileDescriptorSupplier extends BiRequestStreamBaseDescriptorSupplier {
        
        BiRequestStreamFileDescriptorSupplier() {
        }
    }
    
    private static final class BiRequestStreamMethodDescriptorSupplier extends BiRequestStreamBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
        
        private final String methodName;
        
        BiRequestStreamMethodDescriptorSupplier(String methodName) {
            this.methodName = methodName;
        }
    
        @java.lang.Override
        public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
            return getServiceDescriptor().findMethodByName(methodName);
        }
    }
    
    private static volatile io.grpc.ServiceDescriptor serviceDescriptor;
    
    public static io.grpc.ServiceDescriptor getServiceDescriptor() {
        io.grpc.ServiceDescriptor result = serviceDescriptor;
        if (result == null) {
            synchronized (BiRequestStreamGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
                            .setSchemaDescriptor(new BiRequestStreamFileDescriptorSupplier())
                            .addMethod(getRequestBiStreamMethod()).build();
                }
            }
        }
        return result;
    }
}
