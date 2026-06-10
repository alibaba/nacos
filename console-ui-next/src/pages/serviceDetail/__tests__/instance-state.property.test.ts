import { describe, expect, it } from 'vitest';
import fc from 'fast-check';
import {
  patchInstance,
  removeInstance,
  type InstancePatch,
  type InstancesByCluster,
} from '../instance-state';
import type { Instance } from '@/types/service';

const ipArb = fc
  .tuple(fc.nat(255), fc.nat(255), fc.nat(255), fc.nat(255))
  .map((parts) => parts.join('.'));

const instanceArb: fc.Arbitrary<Instance> = fc.record({
  ip: ipArb,
  port: fc.integer({ min: 1, max: 65535 }),
  weight: fc.integer({ min: 0, max: 100 }),
  healthy: fc.boolean(),
  enabled: fc.boolean(),
  ephemeral: fc.boolean(),
  clusterName: fc.string(),
  serviceName: fc.string(),
  metadata: fc.dictionary(fc.string(), fc.string()),
});

const clusterArb = fc.record({
  // Unique ip:port per cluster, mirroring the table's row key.
  list: fc.uniqueArray(instanceArb, { selector: (inst) => `${inst.ip}:${inst.port}` }),
  total: fc.nat(),
  loading: fc.boolean(),
});

const stateArb: fc.Arbitrary<InstancesByCluster> = fc.dictionary(fc.string(), clusterArb);

const patchArb: fc.Arbitrary<InstancePatch> = fc.record(
  {
    enabled: fc.boolean(),
    weight: fc.integer({ min: 0, max: 100 }),
    metadata: fc.dictionary(fc.string(), fc.string()),
  },
  { requiredKeys: [] },
);

const stateWithTargetArb = fc
  .tuple(stateArb, fc.nat(), fc.nat())
  .filter(([state]) => Object.values(state).some((cluster) => cluster.list.length > 0))
  .map(([state, clusterPick, instancePick]) => {
    const clusters = Object.entries(state).filter(([, cluster]) => cluster.list.length > 0);
    const [clusterName, cluster] = clusters[clusterPick % clusters.length];
    const index = instancePick % cluster.list.length;
    return { state, clusterName, index, target: cluster.list[index] };
  });

describe('patchInstance properties', () => {
  it('never mutates its input', () => {
    fc.assert(
      fc.property(
        stateArb,
        fc.string(),
        ipArb,
        fc.integer({ min: 1, max: 65535 }),
        patchArb,
        (state, clusterName, ip, port, patch) => {
          const snapshot = JSON.stringify(state);
          patchInstance(state, clusterName, ip, port, patch);
          expect(JSON.stringify(state)).toBe(snapshot);
        },
      ),
      { numRuns: 100 },
    );
  });

  it('returns the same reference when the target is absent', () => {
    fc.assert(
      fc.property(
        stateArb,
        fc.string(),
        ipArb,
        fc.integer({ min: 1, max: 65535 }),
        patchArb,
        (state, clusterName, ip, port, patch) => {
          const cluster = Object.hasOwn(state, clusterName) ? state[clusterName] : undefined;
          const miss =
            !cluster || !cluster.list.some((inst) => inst.ip === ip && inst.port === port);
          fc.pre(miss);
          expect(patchInstance(state, clusterName, ip, port, patch)).toBe(state);
        },
      ),
      { numRuns: 100 },
    );
  });

  it('merges the patch into the target and leaves everything else reference-equal', () => {
    fc.assert(
      fc.property(stateWithTargetArb, patchArb, ({ state, clusterName, index, target }, patch) => {
        const result = patchInstance(state, clusterName, target.ip, target.port, patch);

        expect(result[clusterName].list[index]).toEqual({ ...target, ...patch });
        expect(result[clusterName].total).toBe(state[clusterName].total);
        expect(result[clusterName].loading).toBe(state[clusterName].loading);
        state[clusterName].list.forEach((inst, i) => {
          if (i !== index) {
            expect(result[clusterName].list[i]).toBe(inst);
          }
        });
        Object.keys(state).forEach((name) => {
          if (name !== clusterName) {
            expect(result[name]).toBe(state[name]);
          }
        });
      }),
      { numRuns: 100 },
    );
  });
});

describe('removeInstance properties', () => {
  it('never mutates its input', () => {
    fc.assert(
      fc.property(
        stateArb,
        fc.string(),
        ipArb,
        fc.integer({ min: 1, max: 65535 }),
        (state, clusterName, ip, port) => {
          const snapshot = JSON.stringify(state);
          removeInstance(state, clusterName, ip, port);
          expect(JSON.stringify(state)).toBe(snapshot);
        },
      ),
      { numRuns: 100 },
    );
  });

  it('returns the same reference when the target is absent', () => {
    fc.assert(
      fc.property(
        stateArb,
        fc.string(),
        ipArb,
        fc.integer({ min: 1, max: 65535 }),
        (state, clusterName, ip, port) => {
          const cluster = Object.hasOwn(state, clusterName) ? state[clusterName] : undefined;
          const miss =
            !cluster || !cluster.list.some((inst) => inst.ip === ip && inst.port === port);
          fc.pre(miss);
          expect(removeInstance(state, clusterName, ip, port)).toBe(state);
        },
      ),
      { numRuns: 100 },
    );
  });

  it('removes exactly the target, decrements total and leaves everything else reference-equal', () => {
    fc.assert(
      fc.property(stateWithTargetArb, ({ state, clusterName, index, target }) => {
        const result = removeInstance(state, clusterName, target.ip, target.port);

        expect(result[clusterName].list).toHaveLength(state[clusterName].list.length - 1);
        expect(
          result[clusterName].list.some(
            (inst) => inst.ip === target.ip && inst.port === target.port,
          ),
        ).toBe(false);
        expect(result[clusterName].total).toBe(Math.max(0, state[clusterName].total - 1));
        expect(result[clusterName].loading).toBe(state[clusterName].loading);
        state[clusterName].list.forEach((inst, i) => {
          if (i !== index) {
            expect(result[clusterName].list).toContain(inst);
          }
        });
        Object.keys(state).forEach((name) => {
          if (name !== clusterName) {
            expect(result[name]).toBe(state[name]);
          }
        });
      }),
      { numRuns: 100 },
    );
  });
});
