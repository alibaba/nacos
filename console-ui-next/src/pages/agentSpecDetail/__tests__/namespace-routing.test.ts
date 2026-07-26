import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const DETAIL_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../index.tsx'),
  'utf-8',
);

const MANAGEMENT_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../../agentSpecManagement/index.tsx'),
  'utf-8',
);

const EDITOR_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../../newAgentSpec/index.tsx'),
  'utf-8',
);

describe('AgentSpec namespace routing', () => {
  it('reads namespaceId from the detail URL before falling back to the store', () => {
    expect(DETAIL_SOURCE).toContain('const [searchParams] = useSearchParams();');
    expect(DETAIL_SOURCE).toContain("searchParams.get('namespaceId') ||");
    expect(DETAIL_SOURCE).toContain("searchParams.get('namespace') ||");
  });

  it('carries namespaceId when opening detail pages from the management page', () => {
    expect(MANAGEMENT_SOURCE).toContain('const params = new URLSearchParams({ namespaceId });');
    expect(MANAGEMENT_SOURCE).toContain(
      'navigate(`/agentspec/${encodeURIComponent(name)}?${params}`);',
    );
  });

  it('carries namespaceId when the editor redirects to the detail page', () => {
    expect(EDITOR_SOURCE).toContain('const params = new URLSearchParams({ namespaceId });');
    expect(EDITOR_SOURCE).toContain(
      'navigate(`/agentspec/${encodeURIComponent(result.name)}?${params}`);',
    );
  });
});
