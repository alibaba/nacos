// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: main/proto/consistency.proto

package com.alibaba.nacos.consistency.entity;

public interface ReadRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:ReadRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string group = 1;</code>
   * @return The group.
   */
  java.lang.String getGroup();
  /**
   * <code>string group = 1;</code>
   * @return The bytes for group.
   */
  com.google.protobuf.ByteString
      getGroupBytes();

  /**
   * <code>bytes data = 2;</code>
   * @return The data.
   */
  com.google.protobuf.ByteString getData();

  /**
   * <code>map&lt;string, string&gt; extendInfo = 3;</code>
   */
  int getExtendInfoCount();
  /**
   * <code>map&lt;string, string&gt; extendInfo = 3;</code>
   */
  boolean containsExtendInfo(
      java.lang.String key);
  /**
   * Use {@link #getExtendInfoMap()} instead.
   */
  @java.lang.Deprecated
  java.util.Map<java.lang.String, java.lang.String>
  getExtendInfo();
  /**
   * <code>map&lt;string, string&gt; extendInfo = 3;</code>
   */
  java.util.Map<java.lang.String, java.lang.String>
  getExtendInfoMap();
  /**
   * <code>map&lt;string, string&gt; extendInfo = 3;</code>
   */

  java.lang.String getExtendInfoOrDefault(
      java.lang.String key,
      java.lang.String defaultValue);
  /**
   * <code>map&lt;string, string&gt; extendInfo = 3;</code>
   */

  java.lang.String getExtendInfoOrThrow(
      java.lang.String key);
}
