/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.istio.api;

/**
 * @author special.fy
 */
public class ApiConstants {

    /**
     * Default api prefix of any type of google protocol buffer.
     */
    public static final String API_TYPE_PREFIX = "type.googleapis.com/";

    /**
     * Istio crd type url for mcp over xds
     * TODO Support other Istio crd, such as gateway, vs, dr and so on.
     */
    public static final String SERVICE_ENTRY_PROTO_PACKAGE = "networking.istio.io/v1alpha3/ServiceEntry";
    public static final String MESH_CONFIG_PROTO_PACKAGE = "core/v1alpha1/MeshConfig";

    /**
     * Istio crd type url for mcp
     */
    public static final String MCP_PREFIX = "istio/";
    public static final String SERVICE_ENTRY_COLLECTION = MCP_PREFIX + "networking/v1alpha3/serviceentries";

    /**
     * Istio crd type url of api.
     */
    public static final String MCP_RESOURCE_PROTO = API_TYPE_PREFIX + "istio.mcp.v1alpha1.Resource";
    public static final String SERVICE_ENTRY_PROTO = API_TYPE_PREFIX + "istio.networking.v1alpha3.ServiceEntry";

    /**
     * Standard xds type url
     * TODO Support lds, rds and sds
     */
    public static final String CLUSTER_TYPE = API_TYPE_PREFIX + "envoy.config.cluster.v3.Cluster";
    public static final String ENDPOINT_TYPE = API_TYPE_PREFIX + "envoy.config.endpoint.v3.ClusterLoadAssignment";
}
