import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const HEADER_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../header.tsx'),
  'utf-8',
);

describe('Header change-password wiring', () => {
  it('imports the ChangePasswordDialog from the layout folder', () => {
    expect(HEADER_SOURCE).toContain(
      "import { ChangePasswordDialog } from '@/components/layout/change-password-dialog'",
    );
  });

  it('manages dialog visibility with local component state', () => {
    expect(HEADER_SOURCE).toContain(
      'const [changePasswordOpen, setChangePasswordOpen] = useState(false);',
    );
  });

  it('opens the dialog when the change-password menu item is selected', () => {
    const menuItemBlock = HEADER_SOURCE.slice(
      HEADER_SOURCE.indexOf("t('header.changePassword')") - 400,
      HEADER_SOURCE.indexOf("t('header.changePassword')") + 80,
    );

    expect(menuItemBlock).toContain('setChangePasswordOpen(true)');
    expect(menuItemBlock).toContain('event.preventDefault()');
  });

  it('renders the dialog only when auth is enabled and the user is not OIDC', () => {
    expect(HEADER_SOURCE).toContain('{authEnabled && !isOidcUser() && (');
    expect(HEADER_SOURCE).toContain(
      '<ChangePasswordDialog open={changePasswordOpen} onOpenChange={setChangePasswordOpen} />',
    );
  });
});

describe('Header namespace switch wiring', () => {
  it('checks the registered namespace guard before updating the namespace store', () => {
    const switchBlock = HEADER_SOURCE.slice(
      HEADER_SOURCE.indexOf('const handleNamespaceChange'),
      HEADER_SOURCE.indexOf('return ('),
    );

    expect(switchBlock).toContain('const guard = getNamespaceChangeGuard();');
    expect(switchBlock).toContain('if (guard && !guard(value, nextShowName))');
    expect(switchBlock.indexOf('const guard = getNamespaceChangeGuard();')).toBeLessThan(
      switchBlock.indexOf('setNamespace(value, nextShowName);'),
    );
  });
});
