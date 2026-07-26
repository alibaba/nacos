import fs from 'fs';
import path from 'path';
import { describe, expect, it } from 'vitest';

const SOURCE = fs.readFileSync(path.resolve(__dirname, '../ToolDetail.tsx'), 'utf-8');
const PREVIEW_SOURCE = SOURCE.slice(SOURCE.indexOf('{/* Templates preview */}'));

describe('ToolDetail template preview', () => {
  it('recognizes all supported json-go-template fields', () => {
    const hasTemplatesSource = SOURCE.slice(
      SOURCE.indexOf('const hasTemplates'),
      SOURCE.indexOf('return (')
    );

    expect(hasTemplatesSource).toContain('tmpl.requestTemplate');
    expect(hasTemplatesSource).toContain('tmpl.argsPosition');
    expect(hasTemplatesSource).toContain('tmpl.responseTemplate');
    expect(hasTemplatesSource).toContain('tmpl.errorResponseTemplate');
  });

  it('renders all fields in the same order as the editor', () => {
    const labels = [
      "t('mcp.requestTemplate')",
      "t('mcp.argsPosition')",
      "t('mcp.responseTemplate')",
      "t('mcp.errorResponseTemplate')",
    ];

    labels.slice(1).forEach((label, index) => {
      expect(PREVIEW_SOURCE.indexOf(labels[index])).toBeLessThan(PREVIEW_SOURCE.indexOf(label));
    });
  });

  it('formats argsPosition as JSON and preserves errorResponseTemplate text', () => {
    expect(PREVIEW_SOURCE).toContain('JSON.stringify(tmpl!.argsPosition, null, 2)');
    expect(PREVIEW_SOURCE).toContain('{tmpl!.errorResponseTemplate}');
    expect(PREVIEW_SOURCE).not.toContain('JSON.stringify(tmpl!.errorResponseTemplate');
  });
});
