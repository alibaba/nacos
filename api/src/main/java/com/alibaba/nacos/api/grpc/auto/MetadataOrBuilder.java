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

public interface MetadataOrBuilder extends
    // @@protoc_insertion_point(interface_extends:Metadata)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string type = 3;</code>
   */
  String getType();
  /**
   * <code>string type = 3;</code>
   */
  com.google.protobuf.ByteString
      getTypeBytes();

  /**
   * <code>string clientIp = 8;</code>
   */
  String getClientIp();
  /**
   * <code>string clientIp = 8;</code>
   */
  com.google.protobuf.ByteString
      getClientIpBytes();

  /**
   * <code>map&lt;string, string&gt; headers = 7;</code>
   */
  int getHeadersCount();
  /**
   * <code>map&lt;string, string&gt; headers = 7;</code>
   */
  boolean containsHeaders(
      String key);
  /**
   * Use {@link #getHeadersMap()} instead.
   */
  @Deprecated
  java.util.Map<String, String>
  getHeaders();
  /**
   * <code>map&lt;string, string&gt; headers = 7;</code>
   */
  java.util.Map<String, String>
  getHeadersMap();
  /**
   * <code>map&lt;string, string&gt; headers = 7;</code>
   */

  String getHeadersOrDefault(
      String key,
      String defaultValue);
  /**
   * <code>map&lt;string, string&gt; headers = 7;</code>
   */

  String getHeadersOrThrow(
      String key);
}
