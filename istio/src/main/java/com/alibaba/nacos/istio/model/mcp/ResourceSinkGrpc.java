package com.alibaba.nacos.istio.model.mcp;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;

/**
 * <pre>
 * Service where the source is the gRPC client. The source is responsible for
 * initiating connections and opening streams.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.17.1)",
    comments = "Source: mcp.proto")
public final class ResourceSinkGrpc {

  private ResourceSinkGrpc() {}

  public static final String SERVICE_NAME = "istio.mcp.v1alpha1.ResourceSink";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.alibaba.nacos.istio.model.mcp.Resources,
      com.alibaba.nacos.istio.model.mcp.RequestResources> getEstablishResourceStreamMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "EstablishResourceStream",
      requestType = com.alibaba.nacos.istio.model.mcp.Resources.class,
      responseType = com.alibaba.nacos.istio.model.mcp.RequestResources.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.alibaba.nacos.istio.model.mcp.Resources,
      com.alibaba.nacos.istio.model.mcp.RequestResources> getEstablishResourceStreamMethod() {
    io.grpc.MethodDescriptor<com.alibaba.nacos.istio.model.mcp.Resources, com.alibaba.nacos.istio.model.mcp.RequestResources> getEstablishResourceStreamMethod;
    if ((getEstablishResourceStreamMethod = ResourceSinkGrpc.getEstablishResourceStreamMethod) == null) {
      synchronized (ResourceSinkGrpc.class) {
        if ((getEstablishResourceStreamMethod = ResourceSinkGrpc.getEstablishResourceStreamMethod) == null) {
          ResourceSinkGrpc.getEstablishResourceStreamMethod = getEstablishResourceStreamMethod = 
              io.grpc.MethodDescriptor.<com.alibaba.nacos.istio.model.mcp.Resources, com.alibaba.nacos.istio.model.mcp.RequestResources>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(
                  "istio.mcp.v1alpha1.ResourceSink", "EstablishResourceStream"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.istio.model.mcp.Resources.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.alibaba.nacos.istio.model.mcp.RequestResources.getDefaultInstance()))
                  .setSchemaDescriptor(new ResourceSinkMethodDescriptorSupplier("EstablishResourceStream"))
                  .build();
          }
        }
     }
     return getEstablishResourceStreamMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ResourceSinkStub newStub(io.grpc.Channel channel) {
    return new ResourceSinkStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ResourceSinkBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new ResourceSinkBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ResourceSinkFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new ResourceSinkFutureStub(channel);
  }

  /**
   * <pre>
   * Service where the source is the gRPC client. The source is responsible for
   * initiating connections and opening streams.
   * </pre>
   */
  public static abstract class ResourceSinkImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * The source, acting as gRPC client, establishes a new resource stream
     * with the sink. The sink sends RequestResources message to and
     * receives Resources messages from the source.
     * </pre>
     */
    public io.grpc.stub.StreamObserver<com.alibaba.nacos.istio.model.mcp.Resources> establishResourceStream(
        io.grpc.stub.StreamObserver<com.alibaba.nacos.istio.model.mcp.RequestResources> responseObserver) {
      return asyncUnimplementedStreamingCall(getEstablishResourceStreamMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getEstablishResourceStreamMethod(),
            asyncBidiStreamingCall(
              new MethodHandlers<
                com.alibaba.nacos.istio.model.mcp.Resources,
                com.alibaba.nacos.istio.model.mcp.RequestResources>(
                  this, METHODID_ESTABLISH_RESOURCE_STREAM)))
          .build();
    }
  }

  /**
   * <pre>
   * Service where the source is the gRPC client. The source is responsible for
   * initiating connections and opening streams.
   * </pre>
   */
  public static final class ResourceSinkStub extends io.grpc.stub.AbstractStub<ResourceSinkStub> {
    private ResourceSinkStub(io.grpc.Channel channel) {
      super(channel);
    }

    private ResourceSinkStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ResourceSinkStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new ResourceSinkStub(channel, callOptions);
    }

    /**
     * <pre>
     * The source, acting as gRPC client, establishes a new resource stream
     * with the sink. The sink sends RequestResources message to and
     * receives Resources messages from the source.
     * </pre>
     */
    public io.grpc.stub.StreamObserver<com.alibaba.nacos.istio.model.mcp.Resources> establishResourceStream(
        io.grpc.stub.StreamObserver<com.alibaba.nacos.istio.model.mcp.RequestResources> responseObserver) {
      return asyncBidiStreamingCall(
          getChannel().newCall(getEstablishResourceStreamMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * <pre>
   * Service where the source is the gRPC client. The source is responsible for
   * initiating connections and opening streams.
   * </pre>
   */
  public static final class ResourceSinkBlockingStub extends io.grpc.stub.AbstractStub<ResourceSinkBlockingStub> {
    private ResourceSinkBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private ResourceSinkBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ResourceSinkBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new ResourceSinkBlockingStub(channel, callOptions);
    }
  }

  /**
   * <pre>
   * Service where the source is the gRPC client. The source is responsible for
   * initiating connections and opening streams.
   * </pre>
   */
  public static final class ResourceSinkFutureStub extends io.grpc.stub.AbstractStub<ResourceSinkFutureStub> {
    private ResourceSinkFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private ResourceSinkFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ResourceSinkFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new ResourceSinkFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_ESTABLISH_RESOURCE_STREAM = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final ResourceSinkImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(ResourceSinkImplBase serviceImpl, int methodId) {
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
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_ESTABLISH_RESOURCE_STREAM:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.establishResourceStream(
              (io.grpc.stub.StreamObserver<com.alibaba.nacos.istio.model.mcp.RequestResources>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class ResourceSinkBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ResourceSinkBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.alibaba.nacos.istio.model.mcp.Mcp.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ResourceSink");
    }
  }

  private static final class ResourceSinkFileDescriptorSupplier
      extends ResourceSinkBaseDescriptorSupplier {
    ResourceSinkFileDescriptorSupplier() {}
  }

  private static final class ResourceSinkMethodDescriptorSupplier
      extends ResourceSinkBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    ResourceSinkMethodDescriptorSupplier(String methodName) {
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
      synchronized (ResourceSinkGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ResourceSinkFileDescriptorSupplier())
              .addMethod(getEstablishResourceStreamMethod())
              .build();
        }
      }
    }
    return result;
  }
}
