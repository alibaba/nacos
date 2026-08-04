import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const SOURCE = fs.readFileSync(path.resolve(__dirname, '../index.tsx'), 'utf-8');

describe('AgentSpec visibility authorization entry', () => {
  it('shows the entry only for the resource owner or a global administrator', () => {
    expect(SOURCE).toContain('const { globalAdmin, username } = useAuthStore();');
    expect(SOURCE).toContain('const canManageVisibility = globalAdmin || detail.owner === username;');
    expect(SOURCE).toContain('{canManageVisibility && (');
  });

  it('keeps draft and lifecycle write operations guarded by resource write permission', () => {
    expect(SOURCE).toContain('const canWriteResource = detail.writable;');
    expect(SOURCE).toContain('{canWriteResource && selectedVersion && currentVersionStatus && (');
    expect(SOURCE).toContain('disabled={enableToggling || !canWriteResource}');
    expect(SOURCE).toContain('disabled={scopeToggling || !canWriteResource}');
    expect(SOURCE).toContain('canWrite={canWriteResource}');
  });

  it('derives the visibility resource identity from the current detail page', () => {
    expect(SOURCE).toContain('<VisibilityAuthorizationDialog');
    expect(SOURCE).toContain('namespaceId={namespaceId}');
    expect(SOURCE).toContain('resourceType="agentspec"');
    expect(SOURCE).toContain('resourceName={agentSpecName}');
    expect(SOURCE).toContain('onSuccess={loadDetail}');
  });
});
