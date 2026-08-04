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
    expect(SOURCE).toContain('authApi.listUsers({ pageNo: 1, pageSize: 500');
    expect(SOURCE).not.toContain('createRole');
    expect(SOURCE).not.toContain('createPermission');
    expect(SOURCE).not.toContain('deletePermission');
  });

  it('lets operators search and select usernames like other authority dialogs', () => {
    expect(SOURCE).toContain("import { ComboInput } from '@/components/ui/combo-input'");
    expect(SOURCE).toContain('<ComboInput');
    expect(SOURCE).toContain('options={users.map((user) => ({ value: user.username, label: user.username }))}');
    expect(SOURCE).toContain("placeholder={t('authority.selectUserPlaceholder')}");
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

  it('uses backend error detail for permission denial and unsupported-plugin feedback', () => {
    expect(SOURCE).toContain('const detail = response?.data?.data');
    expect(SOURCE).toContain("typeof detail === 'string' && detail.trim()");
    expect(SOURCE).toContain('const message = response?.data?.message');
    expect(SOURCE).toContain("getErrorMessage(e) || t('common.requestFailed')");
  });

  it('keeps unsupported-plugin failures local to the dialog instead of breaking the page', () => {
    expect(SOURCE).toContain('} catch (e) {');
    expect(SOURCE).toContain('setError(message);');
    expect(SOURCE).toContain('await onSuccess?.();');
    expect(SOURCE).toContain('onOpenChange(false);');
  });
});
