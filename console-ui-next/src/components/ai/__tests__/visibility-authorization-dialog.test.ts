import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../VisibilityAuthorizationDialog.tsx'),
  'utf-8',
);

describe('VisibilityAuthorizationDialog source contract', () => {
  it('derives resource identity from props and keeps those fields read-only', () => {
    expect(SOURCE).toContain('namespaceId,');
    expect(SOURCE).toContain('resourceType,');
    expect(SOURCE).toContain('resourceName,');
    expect(SOURCE).toContain('<Input value={namespaceId} disabled />');
    expect(SOURCE).toContain('<Input value={resourceType} disabled />');
    expect(SOURCE).toContain('<Input value={resourceName} disabled />');
  });

  it('uses only the visibility grant API and never generic role or permission APIs', () => {
    expect(SOURCE).toContain('authApi.grantVisibility(request)');
    expect(SOURCE).toContain('authApi.revokeVisibility(request)');
    expect(SOURCE).not.toContain('createRole');
    expect(SOURCE).not.toContain('createPermission');
    expect(SOURCE).not.toContain('deletePermission');
  });

  it('supports explicit grant and revoke operations with read/read-write actions', () => {
    expect(SOURCE).toContain("type VisibilityOperation = 'grant' | 'revoke'");
    expect(SOURCE).toContain('<SelectItem value="r">');
    expect(SOURCE).toContain('<SelectItem value="w">');
    expect(SOURCE).toContain('<SelectItem value="grant">');
    expect(SOURCE).toContain('<SelectItem value="revoke">');
  });

  it('shows validation or authorization failures inside the dialog', () => {
    expect(SOURCE).toContain("setError(t('common.visibilityAuthorization.usernameRequired'))");
    expect(SOURCE).toContain('const message = getErrorMessage(e)');
    expect(SOURCE).toContain('{error && (');
  });
});
