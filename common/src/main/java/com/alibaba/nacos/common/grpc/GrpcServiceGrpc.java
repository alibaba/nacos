package com.alibaba.nacos.common.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.17.1)",
    comments = "Source: base_grpc_service.proto")
public final class GrpcServiceGrpc {

  private GrpcServiceGrpc() {}

  public static final String SERVICE_NAME = "GrpcService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.alibaba.nacos.common.grpc.GrpcRequest,
      com.alibaba.nacos.common.grpc.GrpcResponse> getRequestMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "request",
      requestType = com.alibaba.nacos.common.grpc.GrpcRequest.class,
      responseType = com.alibaba.nacos.common.grpc.GrpcResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.alibaba.nacos.common.grpc.GrpcRequest,
      com.alibaba.nacos.common.grpc.GrpcResponse> getRequestMethod() {
    io.grpc.MethodDescriptor<com.alibaba.nacos.common.grpc.GrpcRequest, com.alibaba.nacos.common.grpc.GrpcResponse> getRequestMethod;
    if ((getRequestMethod = GrpcServiceGrpc.getRequestMethod) == null) {
      synchronized (GrpcServiceGrpc.class) {
        if ((getRequestMethod = GrpcServiceGrpc.getRequestMethod) == null) {
          GrpcServiceGrpc.getRequestMethod = getRequestMethod = 
              io.grpc.MethodDescriptor.<com.alibaba.nacos.common.grpc.GrpcRequest, com.alibaba.nacos.common.grpc.GrpcResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "GrpcService", "request"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.common.grpc.GrpcRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.common.grpc.GrpcResponse.getDefaultInstance()))
                  .setSchemaDescriptor(new GrpcServiceMethodDescriptorSupplier("request"))
                  .build();
          }
        }
     }
     return getRequestMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static GrpcServiceStub newStub(io.grpc.Channel channel) {
    return new GrpcServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static GrpcServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new GrpcServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static GrpcServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new GrpcServiceFutureStub(channel);
  }

  /**
   */
  public static abstract class GrpcServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public void request(com.alibaba.nacos.common.grpc.GrpcRequest request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.common.grpc.GrpcResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getRequestMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getRequestMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.alibaba.nacos.common.grpc.GrpcRequest,
                com.alibaba.nacos.common.grpc.GrpcResponse>(
                  this, METHODID_REQUEST)))
          .build();
    }
  }

  /**
   */
  public static final class GrpcServiceStub extends io.grpc.stub.AbstractStub<GrpcServiceStub> {
    private GrpcServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private GrpcServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GrpcServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new GrpcServiceStub(channel, callOptions);
    }

    /**
     */
    public void request(com.alibaba.nacos.common.grpc.GrpcRequest request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.common.grpc.GrpcResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getRequestMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class GrpcServiceBlockingStub extends io.grpc.stub.AbstractStub<GrpcServiceBlockingStub> {
    private GrpcServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private GrpcServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GrpcServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new GrpcServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.alibaba.nacos.common.grpc.GrpcResponse request(com.alibaba.nacos.common.grpc.GrpcRequest request) {
      return blockingUnaryCall(
          getChannel(), getRequestMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class GrpcServiceFutureStub extends io.grpc.stub.AbstractStub<GrpcServiceFutureStub> {
    private GrpcServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private GrpcServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GrpcServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new GrpcServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.alibaba.nacos.common.grpc.GrpcResponse> request(
        com.alibaba.nacos.common.grpc.GrpcRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getRequestMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REQUEST = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final GrpcServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(GrpcServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REQUEST:
          serviceImpl.request((com.alibaba.nacos.common.grpc.GrpcRequest) request,
              (io.grpc.stub.StreamObserver<com.alibaba.nacos.common.grpc.GrpcResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class GrpcServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    GrpcServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.alibaba.nacos.common.grpc.BaseGrpcService.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("GrpcService");
    }
  }

  private static final class GrpcServiceFileDescriptorSupplier
      extends GrpcServiceBaseDescriptorSupplier {
    GrpcServiceFileDescriptorSupplier() {}
  }

  private static final class GrpcServiceMethodDescriptorSupplier
      extends GrpcServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    GrpcServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (GrpcServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new GrpcServiceFileDescriptorSupplier())
              .addMethod(getRequestMethod())
              .build();
        }
      }
    }
    return result;
  }
}
