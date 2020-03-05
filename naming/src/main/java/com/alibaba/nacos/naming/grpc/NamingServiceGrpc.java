package com.alibaba.nacos.naming.grpc;

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
    comments = "Source: naming_grpc_service.proto")
public final class NamingServiceGrpc {

  private NamingServiceGrpc() {}

  public static final String SERVICE_NAME = "NamingService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.alibaba.nacos.naming.grpc.Instance,
      com.alibaba.nacos.core.remoting.grpc.Response> getRegisterInstanceMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "registerInstance",
      requestType = com.alibaba.nacos.naming.grpc.Instance.class,
      responseType = com.alibaba.nacos.core.remoting.grpc.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.alibaba.nacos.naming.grpc.Instance,
      com.alibaba.nacos.core.remoting.grpc.Response> getRegisterInstanceMethod() {
    io.grpc.MethodDescriptor<com.alibaba.nacos.naming.grpc.Instance, com.alibaba.nacos.core.remoting.grpc.Response> getRegisterInstanceMethod;
    if ((getRegisterInstanceMethod = NamingServiceGrpc.getRegisterInstanceMethod) == null) {
      synchronized (NamingServiceGrpc.class) {
        if ((getRegisterInstanceMethod = NamingServiceGrpc.getRegisterInstanceMethod) == null) {
          NamingServiceGrpc.getRegisterInstanceMethod = getRegisterInstanceMethod = 
              io.grpc.MethodDescriptor.<com.alibaba.nacos.naming.grpc.Instance, com.alibaba.nacos.core.remoting.grpc.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "NamingService", "registerInstance"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.naming.grpc.Instance.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.core.remoting.grpc.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new NamingServiceMethodDescriptorSupplier("registerInstance"))
                  .build();
          }
        }
     }
     return getRegisterInstanceMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.alibaba.nacos.naming.grpc.Instance,
      com.alibaba.nacos.core.remoting.grpc.Response> getDeregisterInstanceMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "deregisterInstance",
      requestType = com.alibaba.nacos.naming.grpc.Instance.class,
      responseType = com.alibaba.nacos.core.remoting.grpc.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.alibaba.nacos.naming.grpc.Instance,
      com.alibaba.nacos.core.remoting.grpc.Response> getDeregisterInstanceMethod() {
    io.grpc.MethodDescriptor<com.alibaba.nacos.naming.grpc.Instance, com.alibaba.nacos.core.remoting.grpc.Response> getDeregisterInstanceMethod;
    if ((getDeregisterInstanceMethod = NamingServiceGrpc.getDeregisterInstanceMethod) == null) {
      synchronized (NamingServiceGrpc.class) {
        if ((getDeregisterInstanceMethod = NamingServiceGrpc.getDeregisterInstanceMethod) == null) {
          NamingServiceGrpc.getDeregisterInstanceMethod = getDeregisterInstanceMethod = 
              io.grpc.MethodDescriptor.<com.alibaba.nacos.naming.grpc.Instance, com.alibaba.nacos.core.remoting.grpc.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "NamingService", "deregisterInstance"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.naming.grpc.Instance.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.core.remoting.grpc.Response.getDefaultInstance()))
                  .setSchemaDescriptor(new NamingServiceMethodDescriptorSupplier("deregisterInstance"))
                  .build();
          }
        }
     }
     return getDeregisterInstanceMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.alibaba.nacos.core.remoting.grpc.Query,
      com.alibaba.nacos.core.remoting.grpc.Resource> getQueryInstancesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "queryInstances",
      requestType = com.alibaba.nacos.core.remoting.grpc.Query.class,
      responseType = com.alibaba.nacos.core.remoting.grpc.Resource.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.alibaba.nacos.core.remoting.grpc.Query,
      com.alibaba.nacos.core.remoting.grpc.Resource> getQueryInstancesMethod() {
    io.grpc.MethodDescriptor<com.alibaba.nacos.core.remoting.grpc.Query, com.alibaba.nacos.core.remoting.grpc.Resource> getQueryInstancesMethod;
    if ((getQueryInstancesMethod = NamingServiceGrpc.getQueryInstancesMethod) == null) {
      synchronized (NamingServiceGrpc.class) {
        if ((getQueryInstancesMethod = NamingServiceGrpc.getQueryInstancesMethod) == null) {
          NamingServiceGrpc.getQueryInstancesMethod = getQueryInstancesMethod = 
              io.grpc.MethodDescriptor.<com.alibaba.nacos.core.remoting.grpc.Query, com.alibaba.nacos.core.remoting.grpc.Resource>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(
                  "NamingService", "queryInstances"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.core.remoting.grpc.Query.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.core.remoting.grpc.Resource.getDefaultInstance()))
                  .setSchemaDescriptor(new NamingServiceMethodDescriptorSupplier("queryInstances"))
                  .build();
          }
        }
     }
     return getQueryInstancesMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static NamingServiceStub newStub(io.grpc.Channel channel) {
    return new NamingServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static NamingServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new NamingServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static NamingServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new NamingServiceFutureStub(channel);
  }

  /**
   */
  public static abstract class NamingServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public void registerInstance(com.alibaba.nacos.naming.grpc.Instance request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getRegisterInstanceMethod(), responseObserver);
    }

    /**
     */
    public void deregisterInstance(com.alibaba.nacos.naming.grpc.Instance request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.Response> responseObserver) {
      asyncUnimplementedUnaryCall(getDeregisterInstanceMethod(), responseObserver);
    }

    /**
     */
    public void queryInstances(com.alibaba.nacos.core.remoting.grpc.Query request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.Resource> responseObserver) {
      asyncUnimplementedUnaryCall(getQueryInstancesMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getRegisterInstanceMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.alibaba.nacos.naming.grpc.Instance,
                com.alibaba.nacos.core.remoting.grpc.Response>(
                  this, METHODID_REGISTER_INSTANCE)))
          .addMethod(
            getDeregisterInstanceMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.alibaba.nacos.naming.grpc.Instance,
                com.alibaba.nacos.core.remoting.grpc.Response>(
                  this, METHODID_DEREGISTER_INSTANCE)))
          .addMethod(
            getQueryInstancesMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.alibaba.nacos.core.remoting.grpc.Query,
                com.alibaba.nacos.core.remoting.grpc.Resource>(
                  this, METHODID_QUERY_INSTANCES)))
          .build();
    }
  }

  /**
   */
  public static final class NamingServiceStub extends io.grpc.stub.AbstractStub<NamingServiceStub> {
    private NamingServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private NamingServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NamingServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new NamingServiceStub(channel, callOptions);
    }

    /**
     */
    public void registerInstance(com.alibaba.nacos.naming.grpc.Instance request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getRegisterInstanceMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void deregisterInstance(com.alibaba.nacos.naming.grpc.Instance request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.Response> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getDeregisterInstanceMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void queryInstances(com.alibaba.nacos.core.remoting.grpc.Query request,
        io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.Resource> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getQueryInstancesMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class NamingServiceBlockingStub extends io.grpc.stub.AbstractStub<NamingServiceBlockingStub> {
    private NamingServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private NamingServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NamingServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new NamingServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.alibaba.nacos.core.remoting.grpc.Response registerInstance(com.alibaba.nacos.naming.grpc.Instance request) {
      return blockingUnaryCall(
          getChannel(), getRegisterInstanceMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.alibaba.nacos.core.remoting.grpc.Response deregisterInstance(com.alibaba.nacos.naming.grpc.Instance request) {
      return blockingUnaryCall(
          getChannel(), getDeregisterInstanceMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.alibaba.nacos.core.remoting.grpc.Resource queryInstances(com.alibaba.nacos.core.remoting.grpc.Query request) {
      return blockingUnaryCall(
          getChannel(), getQueryInstancesMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class NamingServiceFutureStub extends io.grpc.stub.AbstractStub<NamingServiceFutureStub> {
    private NamingServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private NamingServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NamingServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new NamingServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.alibaba.nacos.core.remoting.grpc.Response> registerInstance(
        com.alibaba.nacos.naming.grpc.Instance request) {
      return futureUnaryCall(
          getChannel().newCall(getRegisterInstanceMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.alibaba.nacos.core.remoting.grpc.Response> deregisterInstance(
        com.alibaba.nacos.naming.grpc.Instance request) {
      return futureUnaryCall(
          getChannel().newCall(getDeregisterInstanceMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.alibaba.nacos.core.remoting.grpc.Resource> queryInstances(
        com.alibaba.nacos.core.remoting.grpc.Query request) {
      return futureUnaryCall(
          getChannel().newCall(getQueryInstancesMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER_INSTANCE = 0;
  private static final int METHODID_DEREGISTER_INSTANCE = 1;
  private static final int METHODID_QUERY_INSTANCES = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final NamingServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(NamingServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REGISTER_INSTANCE:
          serviceImpl.registerInstance((com.alibaba.nacos.naming.grpc.Instance) request,
              (io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.Response>) responseObserver);
          break;
        case METHODID_DEREGISTER_INSTANCE:
          serviceImpl.deregisterInstance((com.alibaba.nacos.naming.grpc.Instance) request,
              (io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.Response>) responseObserver);
          break;
        case METHODID_QUERY_INSTANCES:
          serviceImpl.queryInstances((com.alibaba.nacos.core.remoting.grpc.Query) request,
              (io.grpc.stub.StreamObserver<com.alibaba.nacos.core.remoting.grpc.Resource>) responseObserver);
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

  private static abstract class NamingServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    NamingServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.alibaba.nacos.naming.grpc.NamingGrpcService.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("NamingService");
    }
  }

  private static final class NamingServiceFileDescriptorSupplier
      extends NamingServiceBaseDescriptorSupplier {
    NamingServiceFileDescriptorSupplier() {}
  }

  private static final class NamingServiceMethodDescriptorSupplier
      extends NamingServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    NamingServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (NamingServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new NamingServiceFileDescriptorSupplier())
              .addMethod(getRegisterInstanceMethod())
              .addMethod(getDeregisterInstanceMethod())
              .addMethod(getQueryInstancesMethod())
              .build();
        }
      }
    }
    return result;
  }
}
