<!--
  Copyright 1999-2026 Alibaba Group Holding Ltd.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# Naming Specs

This directory defines the Nacos Naming domain. Naming specs refine the
[Nacos Design Spec](../design/nacos-design-spec.md), the
[Resource Model Spec](../design/resource-model-spec.md), the
[Foundation Capabilities Spec](../design/foundation-capabilities-spec.md),
the [Cluster Membership Spec](../design/foundation-cluster-membership-spec.md),
the [Remote Connection Lifecycle Spec](../design/foundation-remote-connection-spec.md),
the [Internal RPC And Cluster Request Spec](../design/foundation-internal-rpc-spec.md),
the [AP Consistency Spec](../design/foundation-ap-consistency-spec.md),
the [CP Consistency Spec](../design/foundation-cp-consistency-spec.md),
the [Client Runtime Specs](../client/README.md), and the existing HTTP, gRPC,
SDK, auth, and plugin specs for service discovery.

## Spec Structure

### Top-Level Spec

- [Naming Spec](naming-spec.md): top-level positioning, responsibilities, design
  principles, service type split, interface surfaces, and boundaries.

### Common Specs

- [Naming Resource Spec](naming-resource-spec.md): service, cluster, instance,
  client, subscriber, and service type identity.
- [Naming Discovery And Subscription Spec](naming-discovery-subscription-spec.md):
  query, subscribe, push, fuzzy watch, local cache, and failover semantics.
- [Naming Health And Protection Spec](naming-health-protection-spec.md): health
  checking, enabled status, weight, internal filtering, and protection threshold.
- [Naming Metadata And Selector Spec](naming-metadata-selector-spec.md): service,
  cluster, instance metadata, metadata priority, and selector categories.
- [Naming Ops Spec](naming-ops-spec.md): maintainer, diagnostics, metrics,
  switches, and cleanup boundaries.

### Service-Type-Specific Specs

- [Naming Instance Lifecycle Spec](naming-instance-lifecycle-spec.md): common
  lifecycle plus ephemeral-service and persistent-service registration,
  heartbeat, deregistration, batch registration, and cleanup rules.
- [Naming Consistency And Client State Spec](naming-consistency-client-spec.md):
  common client state plus ephemeral-service AP state, persistent-service CP
  state, indexes, and snapshots.
- [Naming Ephemeral Distro Consistency Spec](naming-ephemeral-distro-consistency-spec.md):
  ephemeral client ownership, Distro sync, verify, anti-entropy, cleanup, and
  eventual visibility.
- [Naming Persistent CP Consistency Spec](naming-persistent-cp-consistency-spec.md):
  persistent instance CP writes, metadata groups, snapshots, recovery, and
  visibility boundaries.

## Implementation Source

These specs are derived from the current `naming`, `api`, `client`, `core`,
`consistency`, and `maintainer-client` implementation. User-facing documents are
supporting references. When implementation and older documents conflict, the
current code is the source for these specs.
