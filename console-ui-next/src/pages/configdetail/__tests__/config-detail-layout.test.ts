import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const DETAIL_SOURCE = fs.readFileSync(path.resolve(__dirname, '../index.tsx'), 'utf-8');

describe('Config detail layout', () => {
  it('keeps secondary metadata collapsed by default', () => {
    expect(DETAIL_SOURCE).toContain('const [metadataOpen, setMetadataOpen] = useState(false);');
    expect(DETAIL_SOURCE).toContain('<Collapsible open={metadataOpen}');
    expect(DETAIL_SOURCE).toContain("t('config.expandMetadata')");
  });

  it('provides a larger content viewer and fullscreen mode', () => {
    expect(DETAIL_SOURCE).toContain('const [contentFullscreen, setContentFullscreen] = useState(false);');
    expect(DETAIL_SOURCE).toContain("'fixed inset-0 z-50 flex flex-col rounded-none border-0 bg-background'");
    expect(DETAIL_SOURCE).toContain("'min-h-0 flex-1 gap-3 py-4'");
    expect(DETAIL_SOURCE).toContain('height="100%"');
    expect(DETAIL_SOURCE).toContain("t('config.viewFullscreen')");
    expect(DETAIL_SOURCE).toContain("event.key === 'Escape'");
  });

  it('fits the page into the viewport without page scrolling', () => {
    expect(DETAIL_SOURCE).toContain('flex h-[calc(100vh-6.5rem)] min-h-[560px] flex-col gap-4');
    expect(DETAIL_SOURCE).not.toContain('calc(100vh - 320px)');
  });
});
