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
public final class GrpcStreamServiceGrpc {

  private GrpcStreamServiceGrpc() {}

  public static final String SERVICE_NAME = "GrpcStreamService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.alibaba.nacos.common.grpc.GrpcRequest,
      com.alibaba.nacos.common.grpc.GrpcResponse> getStreamRequestMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "streamRequest",
      requestType = com.alibaba.nacos.common.grpc.GrpcRequest.class,
      responseType = com.alibaba.nacos.common.grpc.GrpcResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.alibaba.nacos.common.grpc.GrpcRequest,
      com.alibaba.nacos.common.grpc.GrpcResponse> getStreamRequestMethod() {
    io.grpc.MethodDescriptor<com.alibaba.nacos.common.grpc.GrpcRequest, com.alibaba.nacos.common.grpc.GrpcResponse> getStreamRequestMethod;
    if ((getStreamRequestMethod = GrpcStreamServiceGrpc.getStreamRequestMethod) == null) {
      synchronized (GrpcStreamServiceGrpc.class) {
        if ((getStreamRequestMethod = GrpcStreamServiceGrpc.getStreamRequestMethod) == null) {
          GrpcStreamServiceGrpc.getStreamRequestMethod = getStreamRequestMethod = 
              io.grpc.MethodDescriptor.<com.alibaba.nacos.common.grpc.GrpcRequest, com.alibaba.nacos.common.grpc.GrpcResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(
                  "GrpcStreamService", "streamRequest"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.common.grpc.GrpcRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.common.grpc.GrpcResponse.getDefaultInstance()))
                  .setSchemaDescriptor(new GrpcStreamServiceMethodDescriptorSupplier("streamRequest"))
                  .build();
          }
        }
     }
     return getStreamRequestMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static GrpcStreamServiceStub newStub(io.grpc.Channel channel) {
    return new GrpcStreamServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static GrpcStreamServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new GrpcStreamServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static GrpcStreamServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new GrpcStreamServiceFutureStub(channel);
  }

  /**
   */
  public static abstract class GrpcStreamServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public void streamRequest(com.alibaba.nacos.common.grpc.GrpcRequest request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.common.grpc.GrpcResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getStreamRequestMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getStreamRequestMethod(),
            asyncServerStreamingCall(
              new MethodHandlers<
                com.alibaba.nacos.common.grpc.GrpcRequest,
                com.alibaba.nacos.common.grpc.GrpcResponse>(
                  this, METHODID_STREAM_REQUEST)))
          .build();
    }
  }

  /**
   */
  public static final class GrpcStreamServiceStub extends io.grpc.stub.AbstractStub<GrpcStreamServiceStub> {
    private GrpcStreamServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private GrpcStreamServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GrpcStreamServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new GrpcStreamServiceStub(channel, callOptions);
    }

    /**
     */
    public void streamRequest(com.alibaba.nacos.common.grpc.GrpcRequest request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.common.grpc.GrpcResponse> responseObserver) {
      asyncServerStreamingCall(
          getChannel().newCall(getStreamRequestMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class GrpcStreamServiceBlockingStub extends io.grpc.stub.AbstractStub<GrpcStreamServiceBlockingStub> {
    private GrpcStreamServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private GrpcStreamServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GrpcStreamServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new GrpcStreamServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public java.util.Iterator<com.alibaba.nacos.common.grpc.GrpcResponse> streamRequest(
        com.alibaba.nacos.common.grpc.GrpcRequest request) {
      return blockingServerStreamingCall(
          getChannel(), getStreamRequestMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class GrpcStreamServiceFutureStub extends io.grpc.stub.AbstractStub<GrpcStreamServiceFutureStub> {
    private GrpcStreamServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private GrpcStreamServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected GrpcStreamServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new GrpcStreamServiceFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_STREAM_REQUEST = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final GrpcStreamServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(GrpcStreamServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_STREAM_REQUEST:
          serviceImpl.streamRequest((com.alibaba.nacos.common.grpc.GrpcRequest) request,
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

  private static abstract class GrpcStreamServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    GrpcStreamServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.alibaba.nacos.common.grpc.BaseGrpcService.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("GrpcStreamService");
    }
  }

  private static final class GrpcStreamServiceFileDescriptorSupplier
      extends GrpcStreamServiceBaseDescriptorSupplier {
    GrpcStreamServiceFileDescriptorSupplier() {}
  }

  private static final class GrpcStreamServiceMethodDescriptorSupplier
      extends GrpcStreamServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    GrpcStreamServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (GrpcStreamServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new GrpcStreamServiceFileDescriptorSupplier())
              .addMethod(getStreamRequestMethod())
              .build();
        }
      }
    }
    return result;
  }
}
