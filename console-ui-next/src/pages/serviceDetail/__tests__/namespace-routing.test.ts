import { describe, expect, it, vi } from 'vitest';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import fs from 'fs';
import path from 'path';
import { McpDetailContent } from '@/components/ai/mcp/McpDetailContent';
import type { McpServerDetailInfo, McpServiceRef } from '@/types/mcp';

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));

function renderMcpDetail(serviceRef?: McpServiceRef, namespaceId = 'resource-ns') {
  const mcp: McpServerDetailInfo = {
    name: 'namespace-routing-test',
    protocol: 'mcp-sse',
    frontProtocol: 'mcp-sse',
    enabled: true,
    remoteServerConfig: serviceRef ? { serviceRef } : undefined,
  };
  return renderToStaticMarkup(createElement(McpDetailContent, { mcp, namespaceId }));
}

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
    expect(MANAGEMENT_SOURCE).toContain('buildServiceDetailPath(svc.name, svc.groupName, currentNamespace');
    expect(MANAGEMENT_SOURCE).toContain('&namespace=${encodeURIComponent(currentNamespace)}');
  });

  it('reads namespace from the service detail URL before falling back to the store', () => {
    expect(DETAIL_SOURCE).toContain("searchParams.get('namespace')");
    expect(DETAIL_SOURCE).toContain("searchParams.get('namespaceId')");
    expect(DETAIL_SOURCE).toContain('namespaceId: activeNamespace');
  });

  it('keeps service detail back navigation inside the active namespace', () => {
    expect(DETAIL_SOURCE).toContain('buildServiceListPathFromDetail(searchParams, activeNamespace)');
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

  it('passes the resource namespace to the shared MCP detail component', () => {
    expect(MCP_DETAIL_SOURCE).toContain('<McpDetailContent mcp={mcp} namespaceId={namespaceId}');
  });

  it.each([
    { label: 'cross-namespace reference', namespaceId: 'service-ns', groupName: 'DEFAULT_GROUP', serviceName: 'backend', expectedNamespace: 'service-ns' },
    { label: 'legacy reference without namespace', namespaceId: '', groupName: 'DEFAULT_GROUP', serviceName: 'backend', expectedNamespace: 'resource-ns' },
    { label: 'encoded service identity', namespaceId: 'team /研发', groupName: 'group & blue', serviceName: 'backend/name?x=1', expectedNamespace: 'team /研发' },
  ])('links to the exact Naming service identity: $label', (scenario) => {
    const html = renderMcpDetail({
      namespaceId: scenario.namespaceId,
      groupName: scenario.groupName,
      serviceName: scenario.serviceName,
    });
    const href = html.match(/href="(#\/serviceDetail\?[^"]+)"/)?.[1];
    expect(href).toBeDefined();
    const url = new URL(href!.replaceAll('&amp;', '&'), 'http://localhost');
    const query = new URLSearchParams(url.hash.slice(url.hash.indexOf('?') + 1));

    expect(query.get('namespace')).toBe(scenario.expectedNamespace);
    expect(query.get('groupName')).toBe(scenario.groupName);
    expect(query.get('serviceName')).toBe(scenario.serviceName);
  });

  it('does not invent a Naming service link without a service reference', () => {
    expect(renderMcpDetail()).not.toContain('#/serviceDetail?');
  });

  it('clears stale service detail while loading a different namespace', () => {
    expect(SERVICE_STORE_SOURCE).toContain(
      'set({ detailLoading: true, error: null, currentService: null });',
    );
  });
});
