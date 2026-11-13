import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const NEW_CONFIG_SOURCE = fs.readFileSync(path.resolve(__dirname, '../index.tsx'), 'utf-8');

describe('New config compact layout', () => {
  it('uses the same responsive form rows as the config editor', () => {
    expect(NEW_CONFIG_SOURCE).toContain("md:grid-cols-[4rem_minmax(0,1fr)]");
    expect(NEW_CONFIG_SOURCE).toContain('<Card className="flex-1 gap-3 py-4">');
    expect(NEW_CONFIG_SOURCE).toContain('rows={2}');
    expect(NEW_CONFIG_SOURCE).toContain('flex min-h-[calc(100vh-6.5rem)] flex-col gap-4');
    expect(NEW_CONFIG_SOURCE).toContain('relative min-h-[240px] flex-1');
  });

  it('keeps application and tags in collapsed advanced options', () => {
    expect(NEW_CONFIG_SOURCE).toContain(
      'const [advancedOpen, setAdvancedOpen] = useState(false);',
    );
    expect(NEW_CONFIG_SOURCE).toContain('<Collapsible open={advancedOpen}');
    expect(NEW_CONFIG_SOURCE).toContain("t('config.advancedOptions')");
    expect(NEW_CONFIG_SOURCE).toContain('<ConfigTagsInput');
  });
});
