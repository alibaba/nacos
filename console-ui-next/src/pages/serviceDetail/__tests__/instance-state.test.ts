import { describe, expect, it } from 'vitest';
import { patchInstance, removeInstance, type InstancesByCluster } from '../instance-state';
import type { Instance } from '@/types/service';

const makeInstance = (over: Partial<Instance> = {}): Instance => ({
  ip: '10.0.0.1',
  port: 8080,
  weight: 1,
  healthy: true,
  enabled: true,
  ephemeral: true,
  clusterName: 'DEFAULT',
  serviceName: 'demo',
  metadata: {},
  ...over,
});

const makeState = (): InstancesByCluster => ({
  DEFAULT: {
    list: [makeInstance(), makeInstance({ ip: '10.0.0.2', port: 8081 })],
    total: 2,
    loading: false,
  },
  OTHER: {
    list: [makeInstance({ ip: '10.0.0.3', clusterName: 'OTHER' })],
    total: 1,
    loading: false,
  },
});

const deepFreeze = (state: InstancesByCluster) => {
  Object.values(state).forEach((cluster) => {
    cluster.list.forEach((inst) => {
      Object.freeze(inst.metadata);
      Object.freeze(inst);
    });
    Object.freeze(cluster.list);
    Object.freeze(cluster);
  });
  return Object.freeze(state);
};

describe('patchInstance', () => {
  it('patches the target instance and nothing else', () => {
    const prev = makeState();
    const result = patchInstance(prev, 'DEFAULT', '10.0.0.1', 8080, { enabled: false });

    expect(result.DEFAULT.list[0].enabled).toBe(false);
    expect(result.DEFAULT.list[1]).toBe(prev.DEFAULT.list[1]);
    expect(result.DEFAULT.total).toBe(prev.DEFAULT.total);
    expect(result.DEFAULT.loading).toBe(prev.DEFAULT.loading);
    expect(result.OTHER).toBe(prev.OTHER);
  });

  it('returns new references along the patched path so React re-renders', () => {
    const prev = makeState();
    const result = patchInstance(prev, 'DEFAULT', '10.0.0.1', 8080, { enabled: false });

    expect(result).not.toBe(prev);
    expect(result.DEFAULT).not.toBe(prev.DEFAULT);
    expect(result.DEFAULT.list).not.toBe(prev.DEFAULT.list);
    expect(result.DEFAULT.list[0]).not.toBe(prev.DEFAULT.list[0]);
  });

  it('applies a combined edit patch and preserves untouched fields', () => {
    const prev = makeState();
    const result = patchInstance(prev, 'DEFAULT', '10.0.0.2', 8081, {
      weight: 5,
      enabled: false,
      metadata: { env: 'prod' },
    });

    const patched = result.DEFAULT.list[1];
    expect(patched.weight).toBe(5);
    expect(patched.enabled).toBe(false);
    expect(patched.metadata).toEqual({ env: 'prod' });
    expect(patched.ip).toBe('10.0.0.2');
    expect(patched.port).toBe(8081);
    expect(patched.healthy).toBe(true);
    expect(patched.ephemeral).toBe(true);
  });

  it('is a no-op returning the same reference when the cluster is missing', () => {
    const prev = makeState();
    expect(patchInstance(prev, 'NOPE', '10.0.0.1', 8080, { enabled: false })).toBe(prev);
  });

  it('is a no-op returning the same reference when ip:port does not match', () => {
    const prev = makeState();
    expect(patchInstance(prev, 'DEFAULT', '10.0.0.1', 9999, { enabled: false })).toBe(prev);
  });

  it('does not mutate its input', () => {
    const prev = deepFreeze(makeState());
    const snapshot = JSON.stringify(prev);

    patchInstance(prev, 'DEFAULT', '10.0.0.1', 8080, { enabled: false, weight: 9 });
    patchInstance(prev, 'NOPE', '10.0.0.1', 8080, { enabled: false });

    expect(JSON.stringify(prev)).toBe(snapshot);
  });
});

describe('removeInstance', () => {
  it('removes the target instance, decrements total and touches nothing else', () => {
    const prev = makeState();
    const result = removeInstance(prev, 'DEFAULT', '10.0.0.1', 8080);

    expect(result.DEFAULT.list).toHaveLength(1);
    expect(result.DEFAULT.list[0]).toBe(prev.DEFAULT.list[1]);
    expect(result.DEFAULT.total).toBe(1);
    expect(result.DEFAULT.loading).toBe(prev.DEFAULT.loading);
    expect(result.OTHER).toBe(prev.OTHER);
  });

  it('returns new references along the changed path so React re-renders', () => {
    const prev = makeState();
    const result = removeInstance(prev, 'DEFAULT', '10.0.0.1', 8080);

    expect(result).not.toBe(prev);
    expect(result.DEFAULT).not.toBe(prev.DEFAULT);
    expect(result.DEFAULT.list).not.toBe(prev.DEFAULT.list);
  });

  it('empties the cluster when its last listed instance is removed', () => {
    const prev = makeState();
    const result = removeInstance(prev, 'OTHER', '10.0.0.3', 8080);

    expect(result.OTHER.list).toHaveLength(0);
    expect(result.OTHER.total).toBe(0);
  });

  it('is a no-op returning the same reference when the cluster is missing', () => {
    const prev = makeState();
    expect(removeInstance(prev, 'NOPE', '10.0.0.1', 8080)).toBe(prev);
  });

  it('is a no-op returning the same reference when ip:port does not match', () => {
    const prev = makeState();
    expect(removeInstance(prev, 'DEFAULT', '10.0.0.1', 9999)).toBe(prev);
    expect(removeInstance(prev, 'DEFAULT', '10.9.9.9', 8080)).toBe(prev);
  });

  it('does not decrement total below zero on inconsistent state', () => {
    const prev: InstancesByCluster = {
      DEFAULT: { list: [makeInstance()], total: 0, loading: false },
    };
    const result = removeInstance(prev, 'DEFAULT', '10.0.0.1', 8080);

    expect(result.DEFAULT.list).toHaveLength(0);
    expect(result.DEFAULT.total).toBe(0);
  });

  it('does not mutate its input', () => {
    const prev = deepFreeze(makeState());
    const snapshot = JSON.stringify(prev);

    removeInstance(prev, 'DEFAULT', '10.0.0.1', 8080);
    removeInstance(prev, 'NOPE', '10.0.0.1', 8080);

    expect(JSON.stringify(prev)).toBe(snapshot);
  });
});
