/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
vi.mock('@/stores/server-store', () => ({ useServerStore: (selector: (state: { copilotEnabled: boolean }) => unknown) => selector({ copilotEnabled: true }) }));
vi.mock('@/api/agent', () => ({ agentApi: { getRuntimeEndpoints: vi.fn() } }));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
vi.mock('@monaco-editor/react', () => ({ Editor: (props: { value: string; options: { readOnly: boolean } }) => <pre data-readonly={props.options.readOnly}>{props.value}</pre> }));
import { HubResourceContent } from '../HubResourceContent';
import { HubResourceVersionInfo } from '../HubResourceVersionInfo';
import { AgentDetailSidebar } from '@/components/ai/AgentDetailContent';
import { HubSkillVersionInfo } from '@/components/ai/skill/HubSkillVersionInfo';
import type { SharedResource } from '@/api/hub-resource';

const base: SharedResource = { resourceType: 'agentspec', hubEntryId: '501', resourceName: 'same', version: '0.0.2', author: 'publisher', downloadCount: 7 };
function view(detail: SharedResource, content: Record<string, unknown>) {
  return renderToStaticMarkup(<HubResourceContent detail={detail} content={content} sidebar={<div>Hub sidebar</div>} />);
}
describe('resource-specific read-only Hub details', () => {
  it('opens an AgentSpec with empty manifest and displays AGENTS.md instead of generic JSON', () => {
    const html = view(base, { content: '', resource: { agents: { name: 'AGENTS.md', type: 'text', content: '# Visible instructions' } } });
    expect(html).toContain('Visible instructions');
    expect(html).toContain('agentSpec.resources');
    expect(html).toContain('Hub sidebar');
    expect(html).not.toContain('Unexpected end');
    expect(html).not.toContain('hubResource.definition');
  });
  it('opens incomplete manifest text without replacing it or failing the detail page', () => {
    expect(view(base, { content: '{', resource: {} })).toContain('agentSpec.noAgentsFile');
  });
  it('shows Prompt template and variable metadata in a read-only editor', () => {
    const html = view({ ...base, resourceType: 'prompt' }, { template: 'Hello {{name}}', variables: [{ name: 'name', defaultValue: 'Guest', description: 'Your name' }] });
    expect(html).toContain('Hello {{name}}');
    expect(html).toContain('data-readonly="true"');
    expect(html).toContain('Guest');
    expect(html).toContain('Your name');
    expect(html).not.toContain('prompt.submit');
  });
  it('shows Agent protocol metadata, declared endpoints and native descriptor', () => {
    const html = view({ ...base, resourceType: 'agent' }, { callInterfaces: [{ protocol: 'A2A', protocolVersion: '0.3', descriptorMediaType: 'application/json', nativeDescriptor: { name: 'native-agent' }, endpointSets: [{ source: 'DECLARED', endpoints: [{ uri: 'https://agent.example/a2a', transport: 'HTTP+JSON' }] }] }] });
    expect(html).toContain('agent.supportedProtocols');
    expect(html).toContain('https://agent.example/a2a');
    expect(html).toContain('native-agent');
    expect(html).not.toContain('agent.runtimeStatus');
  });
  it('shows MCP service, tools, packages and real client configuration with the display name', () => {
    const html = view({ ...base, resourceType: 'mcp' }, { serverSpecification: { name: '501', protocol: 'stdio', frontProtocol: 'stdio', packages: [{ name: '@example/server', identifier: '@example/server', registryType: 'npm', version: '1.2.3' }] }, toolSpecification: { tools: [{ name: 'weather', description: 'Forecast tool', inputSchema: { type: 'object', properties: {} } }] } });
    expect(html).toContain('mcp.serverName');
    expect(html).toContain('mcp.governance');
    expect(html).not.toContain('Hub sidebar');
    expect(html).toContain('weather');
    expect(html).toContain('@example/server');
    expect(html).toContain('1.2.3');
    expect(html).not.toContain('mcp.publish');
  });
  it('preserves source Naming references and visible connection information', () => {
    const serviceRef = { namespaceId: 'source-ns', groupName: 'internal-group', serviceName: 'internal-mcp::1.0.0', transportProtocol: 'https' };
    const server = { name: '501', protocol: 'mcp-streamable', frontProtocol: 'mcp-streamable' as const, remoteServerConfig: { serviceRef, exportPath: '/mcp' } };
    const detail = { ...base, resourceType: 'mcp' as const, mcp: { ...server, enabled: true, backendEndpoints: [{ protocol: 'https', address: 'mcp.example.com', port: '443', path: '/mcp' }] } };
    const html = view(detail, { serverSpecification: server });
    expect(html).toContain('mcp.serviceRef');
    expect(html).toContain('internal-group');
    expect(html).toContain('internal-mcp::1.0.0');
    expect(html).toContain('mcp-streamable');
    expect(html).toContain('mcp.example.com');
    expect(html).toContain('/mcp');
    expect(server.remoteServerConfig.serviceRef).toBe(serviceRef);
  });
  it('renders the pinned MCP GitHub and website metadata as ordinary external links', () => {
    const html = view({ ...base, resourceType: 'mcp' }, { serverSpecification: { name: '501', protocol: 'stdio', repository: { source: 'github', url: 'https://github.com/frumu-ai/tandem' }, websiteUrl: 'https://tandem.example.com' } });
    expect(html).toMatch(/href="https:\/\/github.com\/frumu-ai\/tandem"[^>]*target="_blank"[^>]*rel="noopener noreferrer"/);
    expect(html).toContain('>github<');
    expect(html).toContain('href="https://tandem.example.com"');
  });
  it('does not fabricate a repository link when the shared MCP version has none', () => {
    const html = view({ ...base, resourceType: 'mcp' }, { serverSpecification: { name: '501', protocol: 'stdio', repository: null, websiteUrl: null } });
    expect(html).not.toContain('github.com');
    expect(html).not.toContain('mcp.repository');
    expect(html).not.toContain('mcp.websiteUrl');
  });
  it('preserves Prompt review nodes, business tags and pinned version labels', () => {
    const html = renderToStaticMarkup(<HubResourceVersionInfo detail={{ ...base, resourceType: 'prompt', tags: ['team-ai'], versionLabels: ['stable'] }}
      content={{ status: 'online', publishPipelineInfo: JSON.stringify({ executionId: 'review-1', status: 'APPROVED', pipeline: [{ nodeId: 'skill-scanner', passed: true }] }) }} install={null} onDownload={() => {}} downloading={false} />);
    expect(html).toContain('prompt.pipelineStatus');
    expect(html).toContain('skill-scanner');
    expect(html).toContain('team-ai');
    expect(html).toContain('stable');
  });
  it('keeps the Agent source two-to-one layout instead of the generic fixed-width sidebar', () => {
    const html = view({ ...base, resourceType: 'agent' }, { version: '0.0.2', callInterfaces: [] });
    expect(html).toContain('xl:grid-cols-3');
    expect(html).toContain('xl:col-span-2');
    expect(html).not.toContain('lg:grid-cols-[minmax(0,1fr)_320px]');
  });
  it('uses the Agent source metadata and read-only custom-label cards instead of generic basic info', () => {
    const html = renderToStaticMarkup(<HubResourceVersionInfo detail={{ ...base, resourceType: 'agent', metadata: {
      owner: 'source-owner', provider: { name: 'source-provider' }, tags: ['agent-tag'],
      versionInfo: { labels: { latest: '0.0.2', stable: '0.0.2', beta: '0.0.3' }, onlineVersions: [{ version: '0.0.2' }] },
    } }} content={{ status: 'online' }} install={null} onDownload={() => {}} downloading={false} />);
    expect(html).toContain('agent.metadata');
    expect(html).toContain('agent.customLabels');
    expect(html).toContain('source-owner');
    expect(html).toContain('source-provider');
    expect(html).toContain('agent-tag');
    expect(html).toContain('>stable<');
    expect(html).not.toContain('→');
    expect(html).not.toContain('beta');
    expect(html).not.toContain('<textarea');
    expect(html).not.toContain('common.save');
    expect(html).not.toContain('basicInfo');
    expect(html).not.toContain('agent.versionStatus.online');
    expect(html).not.toContain('common.bizTags');
  });
  it('shows an empty-label message in Agent Hub without an editable field', () => {
    const html = renderToStaticMarkup(<HubResourceVersionInfo detail={{ ...base, resourceType: 'agent', metadata: { versionInfo: { labels: { latest: '0.0.2', beta: '0.0.3' } } } }} content={{}} install={null} onDownload={() => {}} downloading={false} />);
    expect(html).toContain('common.versionLabels.noLabels');
    expect(html).not.toContain('<textarea');
    expect(html).not.toContain('common.save');
  });
  it('keeps the source Agent JSON editor and save action', () => {
    const html = renderToStaticMarkup(<AgentDetailSidebar agent={{}} labelsText={'{"stable":"0.0.2"}'} onLabelsChange={() => {}} onSave={() => {}} />);
    expect(html).toContain('<textarea');
    expect(html).toContain('stable');
    expect(html).toContain('common.save');
    expect(html).not.toMatch(/readonly=""/i);
  });
  it('does not add generic metadata or tag cards to MCP source content', () => {
    const html = renderToStaticMarkup(<HubResourceVersionInfo detail={{ ...base, resourceType: 'mcp' }} content={{ status: 'online' }} install={null} onDownload={() => {}} downloading={false} />);
    expect(html).toBe('');
  });
  it.each(['prompt', 'agentspec'] as const)('%s retains the selected version status and independent Hub download counts', resourceType => {
    const html = renderToStaticMarkup(<HubResourceVersionInfo detail={{ ...base, resourceType }} content={{ status: 'online', downloadCount: 12345 }} install={null} onDownload={() => {}} downloading={false} />);
    expect(html).toContain('publisher');
    expect(html).toContain('>7<');
    expect(html).not.toContain('12345');
    expect(html).toContain('.status');
    expect(html).not.toContain('draft');
    expect(html).not.toContain('versionDownloads');
  });
  it.each(['mcp', 'agent', 'agentspec'] as const)('%s has no resource download or empty installation card when the source has no native download', resourceType => {
    const html = renderToStaticMarkup(<HubResourceVersionInfo detail={{ ...base, resourceType }} content={{}} install={null} onDownload={() => {}} downloading={false} />);
    expect(html).not.toContain('common.cliUsage.manualDownload');
    expect(html).not.toContain('common.cliUsage.title');
    expect(html).not.toContain('.json');
    expect(html).not.toContain('.zip');
  });
  it('retains AgentSpec CLI installation without adding a manual package download', () => {
    const command = 'nacos-cli agentspec-get same --hub-entry-id 501 --version 0.0.2';
    const html = renderToStaticMarkup(<HubResourceVersionInfo detail={base} content={{}} install={command} onDownload={() => {}} downloading={false} />);
    expect(html).toContain(command);
    expect(html).toContain('common.cliUsage.cliInstall');
    expect(html).not.toContain('common.cliUsage.manualDownload');
    expect(html).not.toContain('same-0.0.2.zip');
  });
  it('preserves the native Prompt Markdown and Skill ZIP downloads', () => {
    const prompt = renderToStaticMarkup(<HubResourceVersionInfo detail={{ ...base, resourceType: 'prompt' }} content={{}} install={null} onDownload={() => {}} downloading={false} />);
    expect(prompt).toContain('common.cliUsage.manualDownload');
    expect(prompt).toContain('same-0.0.2.md');
    const skill = renderToStaticMarkup(<HubSkillVersionInfo detail={{ hubEntryId: '501', displayName: 'same', sharedVersion: '0.0.2', sourceReadable: false, origin: 'HUB' }} onDownload={() => {}} downloading={false} />);
    expect(skill).toContain('common.cliUsage.manualDownload');
    expect(skill).toContain('same-0.0.2.zip');
  });
  it('Skill basic info also omits status and source version download count', () => {
    const html = renderToStaticMarkup(<HubSkillVersionInfo detail={{ hubEntryId: '501', displayName: 'same', sharedVersion: '0.0.2', sourceReadable: false, origin: 'HUB', versionStatus: 'draft', versionDownloadCount: 12345, downloadCount: 7 }} onDownload={() => {}} downloading={false} />);
    expect(html).toContain('>7<');
    expect(html).not.toContain('draft');
    expect(html).not.toContain('skill.status');
    expect(html).not.toContain('12345');
  });
});
