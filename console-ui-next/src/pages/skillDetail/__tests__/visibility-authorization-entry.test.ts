import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const SOURCE = fs.readFileSync(path.resolve(__dirname, '../index.tsx'), 'utf-8');

describe('Skill visibility authorization entry', () => {
  it('shows the entry only for the resource owner or a global administrator', () => {
    expect(SOURCE).toContain('const { globalAdmin, username } = useAuthStore();');
    expect(SOURCE).toContain('const canManageVisibility = globalAdmin || detail.owner === username;');
    expect(SOURCE).toContain('{canManageVisibility && (');
  });

  it('derives the visibility resource identity from the current detail page', () => {
    expect(SOURCE).toContain('<VisibilityAuthorizationDialog');
    expect(SOURCE).toContain('namespaceId={namespaceId}');
    expect(SOURCE).toContain('resourceType="skill"');
    expect(SOURCE).toContain('resourceName={skillName}');
    expect(SOURCE).toContain('onSuccess={loadDetail}');
  });
});
