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

package com.alibaba.nacos.api.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.20.0)", comments = "Source: nacos_grpc_service.proto")
public final class RequestStreamGrpc {
  
  private RequestStreamGrpc() {
  }
  
  public static final String SERVICE_NAME = "RequestStream";
  
  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<GrpcRequest, GrpcResponse> getRequestStreamMethod;
  
  @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
          + "requestStream", requestType = GrpcRequest.class, responseType = GrpcResponse.class, methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<GrpcRequest, GrpcResponse> getRequestStreamMethod() {
    io.grpc.MethodDescriptor<GrpcRequest, GrpcResponse> getRequestStreamMethod;
    if ((getRequestStreamMethod = RequestStreamGrpc.getRequestStreamMethod) == null) {
      synchronized (RequestStreamGrpc.class) {
        if ((getRequestStreamMethod = RequestStreamGrpc.getRequestStreamMethod) == null) {
          RequestStreamGrpc.getRequestStreamMethod = getRequestStreamMethod = io.grpc.MethodDescriptor.<GrpcRequest, GrpcResponse>newBuilder()
                  .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
                  .setFullMethodName(generateFullMethodName("RequestStream", "requestStream"))
                  .setSampledToLocalTracing(true)
                  .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(GrpcRequest.getDefaultInstance()))
                  .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(GrpcResponse.getDefaultInstance()))
                  .setSchemaDescriptor(new RequestStreamMethodDescriptorSupplier("requestStream")).build();
        }
      }
    }
    return getRequestStreamMethod;
  }
  
  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static RequestStreamStub newStub(io.grpc.Channel channel) {
    return new RequestStreamStub(channel);
  }
  
  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static RequestStreamBlockingStub newBlockingStub(io.grpc.Channel channel) {
    return new RequestStreamBlockingStub(channel);
  }
  
  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static RequestStreamFutureStub newFutureStub(io.grpc.Channel channel) {
    return new RequestStreamFutureStub(channel);
  }
  
  /**
   */
  public static abstract class RequestStreamImplBase implements io.grpc.BindableService {
    
    /**
     * <pre>
     * build a streamRequest
     * </pre>
     */
    public void requestStream(GrpcRequest request, io.grpc.stub.StreamObserver<GrpcResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getRequestStreamMethod(), responseObserver);
    }
    
    @Override
    public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor()).addMethod(getRequestStreamMethod(),
              asyncServerStreamingCall(new MethodHandlers<GrpcRequest, GrpcResponse>(this, METHODID_REQUEST_STREAM)))
              .build();
    }
  }
  
  /**
   */
  public static final class RequestStreamStub extends io.grpc.stub.AbstractStub<RequestStreamStub> {
    
    private RequestStreamStub(io.grpc.Channel channel) {
      super(channel);
    }
    
    private RequestStreamStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }
    
    @Override
    protected RequestStreamStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RequestStreamStub(channel, callOptions);
    }
    
    /**
     * <pre>
     * build a streamRequest
     * </pre>
     */
    public void requestStream(GrpcRequest request, io.grpc.stub.StreamObserver<GrpcResponse> responseObserver) {
      asyncServerStreamingCall(getChannel().newCall(getRequestStreamMethod(), getCallOptions()), request,
              responseObserver);
    }
  }
  
  /**
   */
  public static final class RequestStreamBlockingStub extends io.grpc.stub.AbstractStub<RequestStreamBlockingStub> {
    
    private RequestStreamBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }
    
    private RequestStreamBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }
    
    @Override
    protected RequestStreamBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RequestStreamBlockingStub(channel, callOptions);
    }
    
    /**
     * <pre>
     * build a streamRequest
     * </pre>
     */
    public java.util.Iterator<GrpcResponse> requestStream(GrpcRequest request) {
      return blockingServerStreamingCall(getChannel(), getRequestStreamMethod(), getCallOptions(), request);
    }
  }
  
  /**
   */
  public static final class RequestStreamFutureStub extends io.grpc.stub.AbstractStub<RequestStreamFutureStub> {
    
    private RequestStreamFutureStub(io.grpc.Channel channel) {
      super(channel);
    }
    
    private RequestStreamFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }
    
    @Override
    protected RequestStreamFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new RequestStreamFutureStub(channel, callOptions);
    }
  }
  
  private static final int METHODID_REQUEST_STREAM = 0;
  
  private static final class MethodHandlers<Req, Resp>
          implements io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>, io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>, io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>, io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    
    private final RequestStreamImplBase serviceImpl;
    
    private final int methodId;
    
    MethodHandlers(RequestStreamImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REQUEST_STREAM:
          serviceImpl
                  .requestStream((GrpcRequest) request, (io.grpc.stub.StreamObserver<GrpcResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }
  
  private static abstract class RequestStreamBaseDescriptorSupplier
          implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    
    RequestStreamBaseDescriptorSupplier() {
    }
    
    @Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return NacosGrpcService.getDescriptor();
    }
    
    @Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("RequestStream");
    }
  }
  
  private static final class RequestStreamFileDescriptorSupplier extends RequestStreamBaseDescriptorSupplier {
    
    RequestStreamFileDescriptorSupplier() {
    }
  }
  
  private static final class RequestStreamMethodDescriptorSupplier extends RequestStreamBaseDescriptorSupplier
          implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    
    private final String methodName;
    
    RequestStreamMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }
    
    @Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }
  
  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;
  
  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (RequestStreamGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
                  .setSchemaDescriptor(new RequestStreamFileDescriptorSupplier()).addMethod(getRequestStreamMethod())
                  .build();
        }
      }
    }
    return result;
  }
}
