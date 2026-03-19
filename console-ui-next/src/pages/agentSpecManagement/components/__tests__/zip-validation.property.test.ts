/**
 * Property 6: Zip 文件类型验证
 *
 * For any file, the UploadAgentSpecDialog SHALL accept the file if and only if
 * its name ends with .zip or its MIME type is application/zip. All other files
 * SHALL be rejected.
 *
 * **Validates: Requirements 3.1, 3.4**
 */
import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import { isValidZipFile } from '../upload-utils';

// ── Arbitrary generators ───────────────────────────────────────────────────

/** Generate a non-empty file name that does NOT end with .zip (case-insensitive) */
const arbNonZipName: fc.Arbitrary<string> = fc
  .string({ minLength: 1, maxLength: 40 })
  .filter((s) => !s.toLowerCase().endsWith('.zip') && s.trim().length > 0);

/** Generate a file name that ends with .zip (various casings) */
const arbZipName: fc.Arbitrary<string> = fc
  .tuple(
    fc.string({ minLength: 1, maxLength: 30 }).filter((s) => s.trim().length > 0),
    fc.constantFrom('.zip', '.ZIP', '.Zip', '.zIp'),
  )
  .map(([base, ext]) => base + ext);

/** Generate a MIME type that is NOT application/zip */
const arbNonZipMime: fc.Arbitrary<string> = fc
  .constantFrom(
    'application/octet-stream',
    'text/plain',
    'image/png',
    'application/pdf',
    'application/json',
    '',
  );

/** Generate any MIME type (including application/zip) */
const arbAnyMime: fc.Arbitrary<string> = fc.oneof(
  fc.constant('application/zip'),
  arbNonZipMime,
);

// ── Tests ──────────────────────────────────────────────────────────────────

describe('Property 6: Zip 文件类型验证', () => {
  it('property: files with .zip extension are always accepted regardless of MIME type', () => {
    fc.assert(
      fc.property(arbZipName, arbAnyMime, (name, mime) => {
        const file = new File(['content'], name, { type: mime });
        expect(isValidZipFile(file)).toBe(true);
      }),
      { numRuns: 100 },
    );
  });

  it('property: files with application/zip MIME type are always accepted regardless of name', () => {
    fc.assert(
      fc.property(arbNonZipName, (name) => {
        const file = new File(['content'], name, { type: 'application/zip' });
        expect(isValidZipFile(file)).toBe(true);
      }),
      { numRuns: 100 },
    );
  });

  it('property: files without .zip extension AND without application/zip MIME are rejected', () => {
    fc.assert(
      fc.property(arbNonZipName, arbNonZipMime, (name, mime) => {
        const file = new File(['content'], name, { type: mime });
        expect(isValidZipFile(file)).toBe(false);
      }),
      { numRuns: 100 },
    );
  });
});
