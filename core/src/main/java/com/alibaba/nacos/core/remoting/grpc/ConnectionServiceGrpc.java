package com.alibaba.nacos.core.remoting.grpc;

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
public final class ConnectionServiceGrpc {

  private ConnectionServiceGrpc() {}

  public static final String SERVICE_NAME = "ConnectionService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.alibaba.nacos.core.remoting.grpc.GrpcConnection,
      com.alibaba.nacos.core.remoting.grpc.GrpcResource> getStreamConnectionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "streamConnection",
      requestType = com.alibaba.nacos.core.remoting.grpc.GrpcConnection.class,
      responseType = com.alibaba.nacos.core.remoting.grpc.GrpcResource.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.alibaba.nacos.core.remoting.grpc.GrpcConnection,
      com.alibaba.nacos.core.remoting.grpc.GrpcResource> getStreamConnectionMethod() {
    io.grpc.MethodDescriptor<com.alibaba.nacos.core.remoting.grpc.GrpcConnection, com.alibaba.nacos.core.remoting.grpc.GrpcResource> getStreamConnectionMethod;
    if ((getStreamConnectionMethod = ConnectionServiceGrpc.getStreamConnectionMethod) == null) {
      synchronized (ConnectionServiceGrpc.class) {
        if ((getStreamConnectionMethod = ConnectionServiceGrpc.getStreamConnectionMethod) == null) {
          ConnectionServiceGrpc.getStreamConnectionMethod = getStreamConnectionMethod = 
              io.grpc.MethodDescriptor.<com.alibaba.nacos.core.remoting.grpc.GrpcConnection, com.alibaba.nacos.core.remoting.grpc.GrpcResource>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(
                  "ConnectionService", "streamConnection"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.core.remoting.grpc.GrpcConnection.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.core.remoting.grpc.GrpcResource.getDefaultInstance()))
                  .setSchemaDescriptor(new ConnectionServiceMethodDescriptorSupplier("streamConnection"))
                  .build();
          }
        }
     }
     return getStreamConnectionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.alibaba.nacos.core.remoting.grpc.GrpcBeat,
      com.alibaba.nacos.core.remoting.grpc.GrpcResponse> getSendBeatMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "sendBeat",
      requestType = com.alibaba.nacos.core.remoting.grpc.GrpcBeat.class,
      responseType = com.alibaba.nacos.core.remoting.grpc.GrpcResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.alibaba.nacos.core.remoting.grpc.GrpcBeat,
      com.alibaba.nacos.core.remoting.grpc.GrpcResponse> getSendBeatMethod() {
    io.grpc.MethodDescriptor<com.alibaba.nacos.core.remoting.grpc.GrpcBeat, com.alibaba.nacos.core.remoting.grpc.GrpcResponse> getSendBeatMethod;
    if ((getSendBeatMethod = ConnectionServiceGrpc.getSendBeatMethod) == null) {
      synchronized (ConnectionServiceGrpc.class) {
        if ((getSendBeatMethod = ConnectionServiceGrpc.getSendBeatMethod) == null) {
          ConnectionServiceGrpc.getSendBeatMethod = getSendBeatMethod = 
              io.grpc.MethodDescriptor.<com.alibaba.nacos.core.remoting.grpc.GrpcBeat, com.alibaba.nacos.core.remoting.grpc.GrpcResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "ConnectionService", "sendBeat"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.core.remoting.grpc.GrpcBeat.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.core.remoting.grpc.GrpcResponse.getDefaultInstance()))
                  .setSchemaDescriptor(new ConnectionServiceMethodDescriptorSupplier("sendBeat"))
                  .build();
          }
        }
     }
     return getSendBeatMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ConnectionServiceStub newStub(io.grpc.Channel channel) {
    return new ConnectionServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ConnectionServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new ConnectionServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ConnectionServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new ConnectionServiceFutureStub(channel);
  }

  /**
   */
  public static abstract class ConnectionServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public void streamConnection(com.alibaba.nacos.core.remoting.grpc.GrpcConnection request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.GrpcResource> responseObserver) {
      asyncUnimplementedUnaryCall(getStreamConnectionMethod(), responseObserver);
    }

    /**
     */
    public void sendBeat(com.alibaba.nacos.core.remoting.grpc.GrpcBeat request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.GrpcResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getSendBeatMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getStreamConnectionMethod(),
            asyncServerStreamingCall(
              new MethodHandlers<
                com.alibaba.nacos.core.remoting.grpc.GrpcConnection,
                com.alibaba.nacos.core.remoting.grpc.GrpcResource>(
                  this, METHODID_STREAM_CONNECTION)))
          .addMethod(
            getSendBeatMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.alibaba.nacos.core.remoting.grpc.GrpcBeat,
                com.alibaba.nacos.core.remoting.grpc.GrpcResponse>(
                  this, METHODID_SEND_BEAT)))
          .build();
    }
  }

  /**
   */
  public static final class ConnectionServiceStub extends io.grpc.stub.AbstractStub<ConnectionServiceStub> {
    private ConnectionServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private ConnectionServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ConnectionServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new ConnectionServiceStub(channel, callOptions);
    }

    /**
     */
    public void streamConnection(com.alibaba.nacos.core.remoting.grpc.GrpcConnection request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.GrpcResource> responseObserver) {
      asyncServerStreamingCall(
          getChannel().newCall(getStreamConnectionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void sendBeat(com.alibaba.nacos.core.remoting.grpc.GrpcBeat request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.GrpcResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getSendBeatMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class ConnectionServiceBlockingStub extends io.grpc.stub.AbstractStub<ConnectionServiceBlockingStub> {
    private ConnectionServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private ConnectionServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ConnectionServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new ConnectionServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public java.util.Iterator<com.alibaba.nacos.core.remoting.grpc.GrpcResource> streamConnection(
        com.alibaba.nacos.core.remoting.grpc.GrpcConnection request) {
      return blockingServerStreamingCall(
          getChannel(), getStreamConnectionMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.alibaba.nacos.core.remoting.grpc.GrpcResponse sendBeat(com.alibaba.nacos.core.remoting.grpc.GrpcBeat request) {
      return blockingUnaryCall(
          getChannel(), getSendBeatMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class ConnectionServiceFutureStub extends io.grpc.stub.AbstractStub<ConnectionServiceFutureStub> {
    private ConnectionServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private ConnectionServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ConnectionServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new ConnectionServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.alibaba.nacos.core.remoting.grpc.GrpcResponse> sendBeat(
        com.alibaba.nacos.core.remoting.grpc.GrpcBeat request) {
      return futureUnaryCall(
          getChannel().newCall(getSendBeatMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_STREAM_CONNECTION = 0;
  private static final int METHODID_SEND_BEAT = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final ConnectionServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(ConnectionServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_STREAM_CONNECTION:
          serviceImpl.streamConnection((com.alibaba.nacos.core.remoting.grpc.GrpcConnection) request,
              (io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.GrpcResource>) responseObserver);
          break;
        case METHODID_SEND_BEAT:
          serviceImpl.sendBeat((com.alibaba.nacos.core.remoting.grpc.GrpcBeat) request,
              (io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.GrpcResponse>) responseObserver);
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

  private static abstract class ConnectionServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ConnectionServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.alibaba.nacos.core.remoting.grpc.BaseGrpcService.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ConnectionService");
    }
  }

  private static final class ConnectionServiceFileDescriptorSupplier
      extends ConnectionServiceBaseDescriptorSupplier {
    ConnectionServiceFileDescriptorSupplier() {}
  }

  private static final class ConnectionServiceMethodDescriptorSupplier
      extends ConnectionServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    ConnectionServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (ConnectionServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ConnectionServiceFileDescriptorSupplier())
              .addMethod(getStreamConnectionMethod())
              .addMethod(getSendBeatMethod())
              .build();
        }
      }
    }
    return result;
  }
}
