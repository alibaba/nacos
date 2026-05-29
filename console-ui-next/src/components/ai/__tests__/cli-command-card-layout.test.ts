import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const SOURCE = fs.readFileSync(path.resolve(__dirname, '../CliCommandCard.tsx'), 'utf-8');

function getDownloadButtonBlock(): string {
  const match = SOURCE.match(/<Button[\s\S]*?onClick=\{onDownload\}[\s\S]*?<\/Button>/);
  return match?.[0] ?? '';
}

describe('CliCommandCard layout', () => {
  it('constrains the manual download filename inside the download button', () => {
    const downloadButtonBlock = getDownloadButtonBlock();

    expect(downloadButtonBlock).toContain('overflow-hidden');
    expect(downloadButtonBlock).toContain('min-w-0');
    expect(downloadButtonBlock).toContain('truncate');
    expect(downloadButtonBlock).toContain('title={downloadLabel}');
  });
});
