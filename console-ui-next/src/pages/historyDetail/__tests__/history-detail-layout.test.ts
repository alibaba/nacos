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
    expect(HISTORY_DETAIL_SOURCE).toContain('space-y-5 px-6 pb-6 pt-2');
    expect(HISTORY_DETAIL_SOURCE).toContain('grid grid-cols-1 gap-4 px-6 pb-3 pt-4');
  });

  it('uses the same responsive content height as the config detail page', () => {
    expect(HISTORY_DETAIL_SOURCE).toContain(
      'height="max(420px, calc(100vh - 320px))"',
    );
  });
});
