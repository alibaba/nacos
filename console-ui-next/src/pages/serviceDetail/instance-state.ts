import type { Instance } from '@/types/service';

/** Page-local state shape backing the per-cluster instance tables. */
export interface ClusterInstancesState {
  list: Instance[];
  total: number;
  loading: boolean;
}

export type InstancesByCluster = Record<string, ClusterInstancesState>;

/** Fields the console can change on an existing instance row. */
export type InstancePatch = Partial<Pick<Instance, 'enabled' | 'weight' | 'metadata'>>;

/**
 * Immutably applies `patch` to the instance identified by `ip:port` within
 * `clusterName`. Returns `prev` (same reference) when the cluster or the
 * instance cannot be found.
 *
 * Used instead of refetching after an instance update: the console instance
 * list endpoint serves a cached view that is refreshed asynchronously after a
 * write, so an immediate refetch renders stale data (issue #15296). The
 * update response already confirms the values being patched.
 */
export function patchInstance(
  prev: InstancesByCluster,
  clusterName: string,
  ip: string,
  port: number,
  patch: InstancePatch,
): InstancesByCluster {
  if (!Object.hasOwn(prev, clusterName)) {
    return prev;
  }
  const cluster = prev[clusterName];
  const index = cluster.list.findIndex((inst) => inst.ip === ip && inst.port === port);
  if (index === -1) {
    return prev;
  }
  const list = cluster.list.map((inst, i) => (i === index ? { ...inst, ...patch } : inst));
  return { ...prev, [clusterName]: { ...cluster, list } };
}

/**
 * Immutably removes the instance identified by `ip:port` from `clusterName`
 * and decrements the cluster's `total` accordingly. Returns `prev` (same
 * reference) when the cluster or the instance cannot be found.
 *
 * Used instead of refetching after an instance delete for the same reason as
 * {@link patchInstance}: an immediate refetch reads the lagging cached view
 * and can resurrect the row that was just deleted (issue #15296).
 */
export function removeInstance(
  prev: InstancesByCluster,
  clusterName: string,
  ip: string,
  port: number,
): InstancesByCluster {
  if (!Object.hasOwn(prev, clusterName)) {
    return prev;
  }
  const cluster = prev[clusterName];
  const list = cluster.list.filter((inst) => !(inst.ip === ip && inst.port === port));
  if (list.length === cluster.list.length) {
    return prev;
  }
  const total = Math.max(0, cluster.total - (cluster.list.length - list.length));
  return { ...prev, [clusterName]: { ...cluster, list, total } };
}
