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

public final class NacosGrpcService {
  private NacosGrpcService() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_Metadata_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_Metadata_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_Metadata_HeadersEntry_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_Metadata_HeadersEntry_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_Payload_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_Payload_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    String[] descriptorData = {
      "\n\030nacos_grpc_service.proto\032\031google/proto" +
      "buf/any.proto\032\037google/protobuf/timestamp" +
      ".proto\"\203\001\n\010Metadata\022\014\n\004type\030\003 \001(\t\022\020\n\010cli" +
      "entIp\030\010 \001(\t\022\'\n\007headers\030\007 \003(\0132\026.Metadata." +
      "HeadersEntry\032.\n\014HeadersEntry\022\013\n\003key\030\001 \001(" +
      "\t\022\r\n\005value\030\002 \001(\t:\0028\001\"J\n\007Payload\022\033\n\010metad" +
      "ata\030\002 \001(\0132\t.Metadata\022\"\n\004body\030\003 \001(\0132\024.goo" +
      "gle.protobuf.Any28\n\rRequestStream\022\'\n\rreq" +
      "uestStream\022\010.Payload\032\010.Payload\"\0000\0012*\n\007Re" +
      "quest\022\037\n\007request\022\010.Payload\032\010.Payload\"\0002>" +
      "\n\017BiRequestStream\022+\n\017requestBiStream\022\010.P" +
      "ayload\032\010.Payload\"\000(\0010\001B#\n\037com.alibaba.na" +
      "cos.api.grpc.autoP\001b\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
          com.google.protobuf.AnyProto.getDescriptor(),
          com.google.protobuf.TimestampProto.getDescriptor(),
        });
    internal_static_Metadata_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_Metadata_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_Metadata_descriptor,
        new String[] { "Type", "ClientIp", "Headers", });
    internal_static_Metadata_HeadersEntry_descriptor =
      internal_static_Metadata_descriptor.getNestedTypes().get(0);
    internal_static_Metadata_HeadersEntry_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_Metadata_HeadersEntry_descriptor,
        new String[] { "Key", "Value", });
    internal_static_Payload_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_Payload_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_Payload_descriptor,
        new String[] { "Metadata", "Body", });
    com.google.protobuf.AnyProto.getDescriptor();
    com.google.protobuf.TimestampProto.getDescriptor();
  }

  // @@protoc_insertion_point(outer_class_scope)
}
