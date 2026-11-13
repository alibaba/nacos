import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import fs from 'fs';
import path from 'path';
import { Header } from '../header';
import { TooltipProvider } from '@/components/ui/tooltip';

const identity = vi.hoisted(() => ({
  authEnabled: true,
  username: 'alice' as string | null,
  oidc: false,
}));

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  useLocation: () => ({ pathname: '/mcpManagement', search: '', hash: '' }),
}));
vi.mock('@/stores/app-store', () => ({
  useAppStore: () => ({
    theme: 'light', language: 'en-US', sidebarCollapsed: false,
    setTheme: vi.fn(), setLanguage: vi.fn(), toggleSidebar: vi.fn(),
  }),
}));
vi.mock('@/stores/auth-store', () => ({
  useAuthStore: () => ({
    username: identity.username, logout: vi.fn(), isOidcUser: () => identity.oidc,
  }),
}));
vi.mock('@/stores/namespace-store', () => ({
  useNamespaceStore: () => ({
    currentNamespace: 'public', namespaces: [], setNamespace: vi.fn(),
    getNamespaceChangeGuard: () => undefined,
  }),
  getNamespaceSearchAfterSwitch: vi.fn(),
}));
vi.mock('@/stores/server-store', () => ({
  useServerStore: () => ({ authEnabled: identity.authEnabled }),
}));
vi.mock('@/lib/storage', () => ({ getToken: () => identity.username ? 'test-token' : null }));
vi.mock('@/components/layout/change-password-dialog', () => ({
  ChangePasswordDialog: ({ open }: { open: boolean }) => createElement('div', {
    'data-testid': 'change-password-dialog', 'data-open': String(open),
  }),
}));

beforeEach(() => {
  identity.authEnabled = true;
  identity.username = 'alice';
  identity.oidc = false;
});

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

  it.each([
    { label: 'signed-in local user', authEnabled: true, username: 'alice', oidc: false, mounted: true },
    { label: 'anonymous user', authEnabled: true, username: null, oidc: false, mounted: false },
    { label: 'empty username', authEnabled: true, username: '', oidc: false, mounted: false },
    { label: 'OIDC user', authEnabled: true, username: 'alice', oidc: true, mounted: false },
    { label: 'authentication disabled', authEnabled: false, username: 'alice', oidc: false, mounted: false },
  ])('mounts the password dialog only for an authenticated non-OIDC user: $label', (scenario) => {
    Object.assign(identity, scenario);
    const html = renderToStaticMarkup(createElement(TooltipProvider, null, createElement(Header)));

    expect(html.includes('data-testid="change-password-dialog"')).toBe(scenario.mounted);
    if (scenario.mounted) {
      expect(html).toContain('data-open="false"');
    }
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

describe('Header sidebar and user menu wiring', () => {
  it('renders the sidebar toggle before the namespace selector', () => {
    const leftBlock = HEADER_SOURCE.slice(
      HEADER_SOURCE.indexOf('{/* Left - Sidebar toggle and namespace selector */}'),
      HEADER_SOURCE.indexOf('{/* Center - Navigation links */}'),
    );

    expect(HEADER_SOURCE).toContain('sidebarCollapsed');
    expect(HEADER_SOURCE).toContain('toggleSidebar');
    expect(HEADER_SOURCE).toContain('PanelLeft');
    expect(HEADER_SOURCE).toContain('PanelLeftClose');
    expect(leftBlock.indexOf('onClick={toggleSidebar}')).toBeGreaterThan(-1);
    expect(leftBlock.indexOf('onClick={toggleSidebar}')).toBeLessThan(
      leftBlock.indexOf("t('common.selectNamespace')"),
    );
  });

  it('shows a user icon in the dropdown username row', () => {
    const userMenuBlock = HEADER_SOURCE.slice(
      HEADER_SOURCE.indexOf('<DropdownMenuContent align="end"'),
      HEADER_SOURCE.indexOf('<DropdownMenuSeparator />'),
    );

    expect(HEADER_SOURCE).toContain('UserRound');
    expect(userMenuBlock).toContain('<UserRound size={14}');
    expect(userMenuBlock).toContain('<span className="truncate">{username}</span>');
  });
});
