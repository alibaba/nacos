import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const DETAIL_SOURCE = fs.readFileSync(path.resolve(__dirname, '../index.tsx'), 'utf-8');
const MANAGEMENT_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../../serviceManagement/index.tsx'),
  'utf-8',
);
const SUBSCRIBER_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../../subscriberList/index.tsx'),
  'utf-8',
);
const MCP_DETAIL_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../../mcpServerDetail/index.tsx'),
  'utf-8',
);
const SERVICE_STORE_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../../../stores/service-store.ts'),
  'utf-8',
);

describe('Naming namespace routing', () => {
  it('carries the current namespace when opening service detail and subscriber pages', () => {
    expect(MANAGEMENT_SOURCE).toContain('&namespace=${encodeURIComponent(currentNamespace)}');
  });

  it('reads namespace from the service detail URL before falling back to the store', () => {
    expect(DETAIL_SOURCE).toContain("searchParams.get('namespace')");
    expect(DETAIL_SOURCE).toContain("searchParams.get('namespaceId')");
    expect(DETAIL_SOURCE).toContain('namespaceId: activeNamespace');
  });

  it('keeps service detail back navigation inside the active namespace', () => {
    expect(DETAIL_SOURCE).toContain('getServiceManagementPath(activeNamespace)');
  });

  it('guards namespace switching while a service detail dialog is open', () => {
    expect(DETAIL_SOURCE).toContain('setNamespaceChangeGuard(() => {');
    expect(DETAIL_SOURCE).toContain("window.confirm(t('service.namespaceSwitchConfirm'))");
    expect(DETAIL_SOURCE).toContain('closeDetailDialogs();');
  });

  it('uses the active namespace and refetches subscribers when the namespace changes', () => {
    expect(SUBSCRIBER_SOURCE).toContain("searchParams.get('namespace')");
    expect(SUBSCRIBER_SOURCE).toContain("searchParams.get('namespaceId')");
    expect(SUBSCRIBER_SOURCE).toContain('namespaceId: activeNamespace');
    expect(SUBSCRIBER_SOURCE).toContain('}, [activeNamespace, pageNo, pageSize]);');
  });

  it('passes namespace when service detail is opened from MCP service references', () => {
    expect(MCP_DETAIL_SOURCE).toContain('namespace: namespaceId');
    expect(MCP_DETAIL_SOURCE).toContain('navigate(`/serviceDetail?${params.toString()}`);');
  });

  it('clears stale service detail while loading a different namespace', () => {
    expect(SERVICE_STORE_SOURCE).toContain(
      'set({ detailLoading: true, error: null, currentService: null });',
    );
  });
});
