/**
 * Property 5: 文件扩展名到语言模式映射
 *
 * For any file name, the Monaco_Editor language mode SHALL be deterministically
 * mapped: .json → JSON, .md → Markdown, Dockerfile → Dockerfile, all others → plaintext.
 *
 * **Validates: Requirement 6.4**
 */
import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import { getLanguageFromFileName } from '../resource-viewer-utils';

// ── Arbitrary generators ───────────────────────────────────────────────────

/** Generate a random base name (no dots, no slashes) */
const arbBaseName: fc.Arbitrary<string> = fc
  .string({ minLength: 1, maxLength: 30 })
  .filter((s) => !s.includes('.') && !s.includes('/') && s.trim().length > 0);

/** Generate a random path prefix like "some/path/" */
const arbPathPrefix: fc.Arbitrary<string> = fc
  .array(
    fc.string({ minLength: 1, maxLength: 10 }).filter((s) => !s.includes('/') && !s.includes('.') && s.trim().length > 0),
    { minLength: 0, maxLength: 3 },
  )
  .map((parts) => (parts.length > 0 ? parts.join('/') + '/' : ''));

// Known extensions that map to specific languages
const KNOWN_EXTENSIONS = ['.json', '.md', '.js', '.ts', '.yaml', '.yml', '.xml', '.html', '.css', '.sh'];

/** Generate a random extension that is NOT one of the known extensions */
const arbUnknownExtension: fc.Arbitrary<string> = fc
  .string({ minLength: 1, maxLength: 8 })
  .filter((s) => !s.includes('.') && !s.includes('/') && s.trim().length > 0)
  .map((s) => '.' + s.toLowerCase())
  .filter((ext) => !KNOWN_EXTENSIONS.includes(ext));

// ── Tests ──────────────────────────────────────────────────────────────────

describe('Property 5: 文件扩展名到语言模式映射', () => {
  it('property: any filename ending in .json maps to "json"', () => {
    fc.assert(
      fc.property(arbBaseName, (base) => {
        const fileName = base + '.json';
        expect(getLanguageFromFileName(fileName)).toBe('json');
      }),
      { numRuns: 100 },
    );
  });

  it('property: any filename ending in .md maps to "markdown"', () => {
    fc.assert(
      fc.property(arbBaseName, (base) => {
        const fileName = base + '.md';
        expect(getLanguageFromFileName(fileName)).toBe('markdown');
      }),
      { numRuns: 100 },
    );
  });

  it('property: "Dockerfile" or paths ending in "/Dockerfile" map to "dockerfile"', () => {
    fc.assert(
      fc.property(arbPathPrefix, (prefix) => {
        // Bare "Dockerfile"
        expect(getLanguageFromFileName('Dockerfile')).toBe('dockerfile');

        // Path ending in "/Dockerfile"
        if (prefix.length > 0) {
          expect(getLanguageFromFileName(prefix + 'Dockerfile')).toBe('dockerfile');
        }
      }),
      { numRuns: 100 },
    );
  });

  it('property: any filename without a known extension maps to "plaintext"', () => {
    fc.assert(
      fc.property(arbBaseName, arbUnknownExtension, (base, ext) => {
        const fileName = base + ext;
        expect(getLanguageFromFileName(fileName)).toBe('plaintext');
      }),
      { numRuns: 100 },
    );
  });

  it('property: filenames with no extension (no dot) map to "plaintext"', () => {
    fc.assert(
      fc.property(
        arbBaseName.filter((s) => s !== 'Dockerfile'),
        (base) => {
          expect(getLanguageFromFileName(base)).toBe('plaintext');
        },
      ),
      { numRuns: 100 },
    );
  });

  it('property: the mapping is deterministic — calling twice with the same input gives the same output', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 50 }).filter((s) => s.trim().length > 0),
        (fileName) => {
          const result1 = getLanguageFromFileName(fileName);
          const result2 = getLanguageFromFileName(fileName);
          expect(result1).toBe(result2);
        },
      ),
      { numRuns: 100 },
    );
  });
});
