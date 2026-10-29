import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const HISTORY_DETAIL_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../index.tsx'),
  'utf-8',
);

describe('History detail compact layout', () => {
  it('collapses metadata by default and matches the config detail spacing', () => {
    expect(HISTORY_DETAIL_SOURCE).toContain(
      'const [metadataOpen, setMetadataOpen] = useState(false);',
    );
    expect(HISTORY_DETAIL_SOURCE).toContain(
      '<Collapsible open={metadataOpen} onOpenChange={setMetadataOpen} asChild>',
    );
    expect(HISTORY_DETAIL_SOURCE).toContain('grid grid-cols-1 gap-4 px-6 pb-3 pt-4');
  });

  it('fits the page into the viewport like the config detail page', () => {
    expect(HISTORY_DETAIL_SOURCE).toContain('flex h-[calc(100vh-6.5rem)] min-h-[560px] flex-col gap-4');
    expect(HISTORY_DETAIL_SOURCE).toContain('<Card className="min-h-0 flex-1 gap-3 py-4">');
    expect(HISTORY_DETAIL_SOURCE).toContain('height="100%"');
    expect(HISTORY_DETAIL_SOURCE).not.toContain('calc(100vh - 320px)');
  });
});
