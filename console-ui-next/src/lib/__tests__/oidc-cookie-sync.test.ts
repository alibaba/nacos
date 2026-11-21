import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import ts from 'typescript';

const REPO_ROOT = path.resolve(__dirname, '../../../..');
const NEXT_MAIN_PATH = path.join(REPO_ROOT, 'console-ui-next/src/main.tsx');
const LEGACY_INDEX_PATH = path.join(REPO_ROOT, 'console-ui/src/index.js');
const LEGACY_MENU_PATH = path.join(REPO_ROOT, 'console-ui/src/layouts/menu.js');
const LEGACY_LAYOUT_PATH = path.join(REPO_ROOT, 'console-ui/src/layouts/MainLayout.js');

interface CookieRunResult {
  localStorageMap: Map<string, string>;
  cookieWrites: string[];
}

function runOidcSyncSnippet(snippetJs: string, pathname: string, initialCookies: string): CookieRunResult {
  const localStorageMap = new Map<string, string>();
  const sessionStorageMap = new Map<string, string>();
  const cookieWrites: string[] = [];

  const documentObj = {
    get cookie() {
      return initialCookies;
    },
    set cookie(val: string) {
      cookieWrites.push(val);
    },
  };

  const windowObj = {
    location: {
      hash: '',
      href: `https://nacos.example${pathname}`,
      pathname,
    },
    history: {
      replaceState: () => {},
    },
  };

  const context = {
    window: windowObj,
    document: documentObj,
    localStorage: {
      getItem: (k: string) => localStorageMap.get(k) ?? null,
      setItem: (k: string, v: string) => localStorageMap.set(k, v),
      removeItem: (k: string) => localStorageMap.delete(k),
    },
    sessionStorage: {
      getItem: (k: string) => sessionStorageMap.get(k) ?? null,
      setItem: (k: string, v: string) => sessionStorageMap.set(k, v),
      removeItem: (k: string) => sessionStorageMap.delete(k),
    },
    URLSearchParams,
    console,
  };

  vm.runInNewContext(snippetJs, context);
  return { localStorageMap, cookieWrites };
}

function extractNextSyncJs(): string {
  const source = fs.readFileSync(NEXT_MAIN_PATH, 'utf-8');
  const start = source.indexOf('(function oidcCookieSync()');
  const end = source.indexOf('createRoot(');
  const tsSnippet = source.slice(start, end);
  return ts.transpileModule(tsSnippet, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.None },
  }).outputText;
}

function extractLegacySyncJs(): string {
  const source = fs.readFileSync(LEGACY_INDEX_PATH, 'utf-8');
  const start = source.indexOf('(function() {\n  const hash = window.location.hash;');
  const end = source.indexOf('ReactDOM.render(');
  return source.slice(start, end);
}

interface LegacyMenuItem {
  key: string;
  url?: string;
  children?: { key: string; url: string }[];
}

function loadLegacyMenuFn(tokenValue: string): (model?: string, aiEnabled?: boolean, authSystemType?: string) => LegacyMenuItem[] {
  const source = fs.readFileSync(LEGACY_MENU_PATH, 'utf-8');
  const transformed = source
    .replace("import { isJsonString } from '../utils/nacosutil';", '')
    .replace('export const McpServerManagementRoute =', 'const McpServerManagementRoute =')
    .replace('export default function(', 'function getMenuData(');
  const context = {
    localStorage: { token: tokenValue },
    isJsonString: (str: string) => {
      try {
        const obj = JSON.parse(str);
        return typeof obj === 'object' && !!obj;
      } catch {
        return false;
      }
    },
  };
  vm.runInNewContext(`${transformed}\nthis.__getMenuData = getMenuData;`, context);
  return (context as unknown as { __getMenuData: (model?: string, aiEnabled?: boolean, authSystemType?: string) => LegacyMenuItem[] }).__getMenuData;
}

describe('OIDC cookie sync and contextPath cleanup', () => {
  const nextJs = extractNextSyncJs();
  const legacyJs = extractLegacySyncJs();

  it.each([
    { name: 'next console', js: nextJs, pathname: '/nacos/next/' },
    { name: 'next console subpage', js: nextJs, pathname: '/nacos/next/index.html' },
    { name: 'legacy console', js: legacyJs, pathname: '/nacos/legacy/' },
    { name: 'context root', js: nextJs, pathname: '/nacos/' },
  ])('syncs globalAdmin=true and deletes cookies on both contextPath and root for $name', ({ js, pathname }) => {
    const { localStorageMap, cookieWrites } = runOidcSyncSnippet(
      js,
      pathname,
      'accessToken=tok-123; username=alice%20bob; globalAdmin=true',
    );

    expect(JSON.parse(localStorageMap.get('token')!)).toEqual({
      accessToken: 'tok-123',
      username: 'alice bob',
      globalAdmin: true,
      oidc: true,
    });

    for (const cookieName of ['accessToken', 'username', 'globalAdmin']) {
      expect(cookieWrites.some((w) => w.startsWith(`${cookieName}=; Path=/nacos/; Expires=`))).toBe(true);
      expect(cookieWrites.some((w) => w.startsWith(`${cookieName}=; Path=/; Expires=`))).toBe(true);
    }
  });

  it('converts missing or false globalAdmin cookie to boolean false and clears root cookies cleanly', () => {
    const falseRun = runOidcSyncSnippet(
      nextJs,
      '/next/',
      'accessToken=tok-1; username=bob; globalAdmin=false',
    );
    expect(JSON.parse(falseRun.localStorageMap.get('token')!).globalAdmin).toBe(false);
    expect(falseRun.cookieWrites).toHaveLength(3);
    expect(falseRun.cookieWrites.every((w) => w.includes('Path=/;'))).toBe(true);

    const missingRun = runOidcSyncSnippet(
      legacyJs,
      '/legacy/',
      'accessToken=tok-2; username=carol',
    );
    expect(JSON.parse(missingRun.localStorageMap.get('token')!).globalAdmin).toBe(false);
    expect(missingRun.cookieWrites).toHaveLength(3);
  });
});

describe('Legacy console menu isolation for OIDC users', () => {
  it('hides authorityControl for OIDC global admins while keeping cluster and plugin menus', () => {
    const getMenuForOidcAdmin = loadLegacyMenuFn(
      JSON.stringify({ accessToken: 't', username: 'admin', globalAdmin: true, oidc: true }),
    );
    const oidcAdminKeys = getMenuForOidcAdmin('', true, 'oidc').map((m) => m.key);
    expect(oidcAdminKeys).not.toContain('authorityControl');
    expect(oidcAdminKeys).toContain('clusterManagementVirtual');
    expect(oidcAdminKeys).toContain('pluginManagement');

    const getMenuForAuthTypeOidc = loadLegacyMenuFn(
      JSON.stringify({ accessToken: 't', username: 'admin', globalAdmin: true }),
    );
    expect(getMenuForAuthTypeOidc('', true, 'oidc').map((m) => m.key)).not.toContain('authorityControl');

    const getMenuForLocalAdmin = loadLegacyMenuFn(
      JSON.stringify({ accessToken: 't', username: 'nacos', globalAdmin: true, oidc: false }),
    );
    expect(getMenuForLocalAdmin('', true, 'nacos').map((m) => m.key)).toContain('authorityControl');
  });

  it('passes authSystemType from MainLayout to getMenuData', () => {
    const layoutSource = fs.readFileSync(LEGACY_LAYOUT_PATH, 'utf-8');
    expect(layoutSource).toContain('authSystemType: PropTypes.string');
    expect(layoutSource).toContain('this.props.authSystemType');
    expect(layoutSource).toContain('getMenuData(functionMode, aiEnabled, authSystemType)');
  });
});
