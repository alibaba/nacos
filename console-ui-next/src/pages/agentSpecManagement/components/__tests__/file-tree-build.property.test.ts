/**
 * Property 4: 文件树构建正确性
 *
 * For any resource map (Record<string, AgentSpecResource>), the FileTreePanel
 * SHALL group resources into virtual folders by their type field
 * (config/, skill/, cron/, dockerfile/, other/), with manifest.json always as
 * the root node. No empty virtual folders shall be present in the resulting tree.
 *
 * **Validates: Requirements 6.2, 6.6**
 */
import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import type { AgentSpecResource } from '@/types/agentspec';
import { buildFileTree } from '../file-tree-utils';

// ── Arbitrary generators ───────────────────────────────────────────────────

const RESOURCE_TYPES = ['config', 'skill', 'cron', 'dockerfile', 'other'] as const;

/** Generate a single AgentSpecResource with a random type */
const arbResource: fc.Arbitrary<AgentSpecResource> = fc.record({
  name: fc.string({ minLength: 1, maxLength: 30 }).filter((s) => !s.includes('/')),
  type: fc.constantFrom(...RESOURCE_TYPES),
  content: fc.string(),
  metadata: fc.constant(null),
});

/** Generate a resource map with unique keys */
const arbResourceMap: fc.Arbitrary<Record<string, AgentSpecResource>> = fc
  .uniqueArray(arbResource, { selector: (r) => r.name, maxLength: 30 })
  .map((resources) => {
    const map: Record<string, AgentSpecResource> = {};
    for (const r of resources) {
      map[r.name] = r;
    }
    return map;
  });

// ── Tests ──────────────────────────────────────────────────────────────────

describe('Property 4: 文件树构建正确性', () => {
  it('property: manifest.json is always the first node in the tree', () => {
    fc.assert(
      fc.property(arbResourceMap, (resources) => {
        const tree = buildFileTree(resources, '{}');

        expect(tree.length).toBeGreaterThanOrEqual(1);
        expect(tree[0].key).toBe('manifest.json');
        expect(tree[0].name).toBe('manifest.json');
        expect(tree[0].type).toBe('file');
      }),
      { numRuns: 100 },
    );
  });

  it('property: resources are grouped into correct virtual folders by type', () => {
    fc.assert(
      fc.property(arbResourceMap, (resources) => {
        const tree = buildFileTree(resources, '{}');

        // Collect all folder nodes (skip manifest.json at index 0)
        const folders = tree.filter((n) => n.type === 'folder');

        // Every resource must appear inside a folder matching its type
        for (const resource of Object.values(resources)) {
          const expectedFolderType = resource.type || 'other';
          const folder = folders.find((f) => f.resourceType === expectedFolderType);

          expect(folder).toBeDefined();
          const child = folder!.children!.find((c) => c.name === resource.name);
          expect(child).toBeDefined();
          expect(child!.key).toBe(`${expectedFolderType}/${resource.name}`);
        }
      }),
      { numRuns: 100 },
    );
  });

  it('property: no empty folders exist in the tree', () => {
    fc.assert(
      fc.property(arbResourceMap, (resources) => {
        const tree = buildFileTree(resources, '{}');

        const folders = tree.filter((n) => n.type === 'folder');
        for (const folder of folders) {
          expect(folder.children).toBeDefined();
          expect(folder.children!.length).toBeGreaterThan(0);
        }
      }),
      { numRuns: 100 },
    );
  });

  it('property: total file count in folders equals the number of resources', () => {
    fc.assert(
      fc.property(arbResourceMap, (resources) => {
        const tree = buildFileTree(resources, '{}');

        const folders = tree.filter((n) => n.type === 'folder');
        const totalFiles = folders.reduce(
          (sum, f) => sum + (f.children?.length ?? 0),
          0,
        );

        expect(totalFiles).toBe(Object.keys(resources).length);
      }),
      { numRuns: 100 },
    );
  });

  it('property: only valid folder types appear in the tree', () => {
    fc.assert(
      fc.property(arbResourceMap, (resources) => {
        const tree = buildFileTree(resources, '{}');

        const folders = tree.filter((n) => n.type === 'folder');
        const validTypes = new Set(RESOURCE_TYPES);

        for (const folder of folders) {
          expect(validTypes.has(folder.resourceType as typeof RESOURCE_TYPES[number])).toBe(true);
        }
      }),
      { numRuns: 100 },
    );
  });
});
