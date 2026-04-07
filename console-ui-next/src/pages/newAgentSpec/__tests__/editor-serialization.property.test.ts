/**
 * Property 12: 编辑器文件序列化往返
 *
 * For any set of files in the editor file tree, serializing them into content
 * (manifest.json) + resource map for API submission, then reconstructing the
 * file tree from that data, SHALL produce an equivalent file tree structure.
 *
 * **Validates: Requirement 7.4**
 */
import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import { serializeFileTree, deserializeToFiles } from '../editor-utils';
import type { EditorFile } from '../editor-utils';

// ── Arbitrary generators ───────────────────────────────────────────────────

const RESOURCE_TYPES = ['config', 'skill', 'cron', 'dockerfile', 'other'] as const;

const MANIFEST_KEY = 'manifest.json';

/** Generate a non-manifest file name (no slashes, non-empty, not "manifest.json") */
const arbFileName: fc.Arbitrary<string> = fc
  .string({ minLength: 1, maxLength: 30 })
  .filter((s) => !s.includes('/') && s !== MANIFEST_KEY && s.trim().length > 0);

/** Generate a single EditorFile with a resource type */
const arbEditorFile = (type: fc.Arbitrary<string>): fc.Arbitrary<EditorFile> =>
  fc.record({
    content: fc.string({ maxLength: 200 }),
    type,
  });

/** Generate a file map that always includes manifest.json */
const arbFileMap: fc.Arbitrary<Map<string, EditorFile>> = fc
  .tuple(
    // manifest.json content (any string is valid)
    fc.string({ maxLength: 200 }),
    // resource files: unique names with resource types
    fc.uniqueArray(
      fc.tuple(arbFileName, arbEditorFile(fc.constantFrom(...RESOURCE_TYPES))),
      { selector: ([name]) => name, maxLength: 20 },
    ),
  )
  .map(([manifestContent, resourceEntries]) => {
    const files = new Map<string, EditorFile>();
    // manifest.json is always present
    files.set(MANIFEST_KEY, { content: manifestContent, type: 'manifest' });
    for (const [name, file] of resourceEntries) {
      files.set(name, file);
    }
    return files;
  });

// ── Tests ──────────────────────────────────────────────────────────────────

describe('Property 12: 编辑器文件序列化往返', () => {
  it('property: serialize → deserialize roundtrip preserves all file keys', () => {
    fc.assert(
      fc.property(arbFileMap, (files) => {
        const { content, resource } = serializeFileTree(files);
        const restored = deserializeToFiles(content, resource);

        // Same set of keys
        const originalKeys = new Set(files.keys());
        const restoredKeys = new Set(restored.keys());
        expect(restoredKeys).toEqual(originalKeys);
      }),
      { numRuns: 200 },
    );
  });

  it('property: serialize → deserialize roundtrip preserves manifest.json content', () => {
    fc.assert(
      fc.property(arbFileMap, (files) => {
        const { content, resource } = serializeFileTree(files);
        const restored = deserializeToFiles(content, resource);

        const originalManifest = files.get(MANIFEST_KEY)!;
        const restoredManifest = restored.get(MANIFEST_KEY)!;
        expect(restoredManifest.content).toBe(originalManifest.content);
      }),
      { numRuns: 200 },
    );
  });

  it('property: serialize → deserialize roundtrip preserves resource file content and types', () => {
    fc.assert(
      fc.property(arbFileMap, (files) => {
        const { content, resource } = serializeFileTree(files);
        const restored = deserializeToFiles(content, resource);

        // Check every non-manifest file
        for (const [key, original] of files) {
          if (key === MANIFEST_KEY) continue;
          const restoredFile = restored.get(key);
          expect(restoredFile).toBeDefined();
          expect(restoredFile!.content).toBe(original.content);
          expect(restoredFile!.type).toBe(original.type);
        }
      }),
      { numRuns: 200 },
    );
  });

  it('property: file count is preserved through roundtrip', () => {
    fc.assert(
      fc.property(arbFileMap, (files) => {
        const { content, resource } = serializeFileTree(files);
        const restored = deserializeToFiles(content, resource);

        expect(restored.size).toBe(files.size);
      }),
      { numRuns: 200 },
    );
  });

  it('property: serialized resource map has exactly the non-manifest files', () => {
    fc.assert(
      fc.property(arbFileMap, (files) => {
        const { resource } = serializeFileTree(files);

        const nonManifestKeys = [...files.keys()].filter((k) => k !== MANIFEST_KEY);
        const resourceKeys = Object.keys(resource);

        expect(resourceKeys.sort()).toEqual(nonManifestKeys.sort());
      }),
      { numRuns: 200 },
    );
  });
});
