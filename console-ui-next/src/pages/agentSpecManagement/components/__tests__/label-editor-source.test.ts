import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../LabelEditor.tsx'),
  'utf-8',
);

describe('LabelEditor source', () => {
  it('renders existing labels as badges and keeps a single key/value input row for adding', () => {
    expect(SOURCE).toContain('<Badge');
    expect(SOURCE).toContain("variant=\"secondary\"");
    expect(SOURCE).toContain("placeholder={t('agentSpec.labelKey')}");
    expect(SOURCE).toContain("placeholder={t('agentSpec.labelValue')}");
    expect(SOURCE).not.toContain('handleValueChange');
  });
});