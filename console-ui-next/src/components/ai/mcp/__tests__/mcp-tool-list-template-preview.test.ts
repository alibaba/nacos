import fs from 'fs';
import path from 'path';
import { describe, expect, it } from 'vitest';

const SOURCE = fs.readFileSync(path.resolve(__dirname, '../McpToolList.tsx'), 'utf-8');
const ADVANCED_CONFIG_SOURCE = SOURCE.slice(
  SOURCE.indexOf('{/* Protocol Conversion / Templates */}')
);

describe('McpToolList template preview', () => {
  it('recognizes all supported json-go-template fields', () => {
    expect(SOURCE).toContain('const hasRequestTemplate');
    expect(SOURCE).toContain('const hasArgsPosition');
    expect(SOURCE).toContain('const hasResponseTemplate');
    expect(SOURCE).toContain('const hasErrorResponseTemplate');
    expect(SOURCE).toContain('hasArgsPosition ||');
    expect(SOURCE).toContain('hasErrorResponseTemplate ||');
  });

  it('renders all fields in the same order as the editor', () => {
    const sections = [
      '<RequestTemplateSection',
      '<ArgsPositionSection',
      '<ResponseTemplateSection',
      '<ErrorResponseTemplateSection',
    ];

    sections.slice(1).forEach((section, index) => {
      expect(ADVANCED_CONFIG_SOURCE.indexOf(sections[index])).toBeLessThan(
        ADVANCED_CONFIG_SOURCE.indexOf(section)
      );
    });
  });

  it('formats argsPosition as JSON and preserves errorResponseTemplate text', () => {
    expect(SOURCE).toContain('JSON.stringify(data, null, 2)');
    expect(SOURCE).toContain('<ErrorResponseTemplateSection data={tmpl!.errorResponseTemplate as string} />');
    expect(SOURCE).toContain('{data}');
  });
});
