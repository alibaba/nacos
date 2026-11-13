import JSZip from 'jszip';
import type { SharedResource } from '@/api/hub-resource';

/** Export resource definitions without internal shared namespace or database coordinates. */
export function resourceDefinition(detail: SharedResource, content: Record<string, unknown>): unknown {
  if (detail.resourceType === 'prompt') return { promptKey: detail.resourceName, version: detail.version,
    template: content.template, variables: content.variables };
  if (detail.resourceType === 'agent') return { agentName: detail.resourceName, version: detail.version,
    callInterfaces: content.callInterfaces };
  if (detail.resourceType === 'mcp') {
    const server = content.serverSpecification;
    if (!server || typeof server !== 'object' || Array.isArray(server)) throw new Error('Missing MCP definition');
    return { serverSpecification: { ...server, name: detail.resourceName, namespaceId: undefined,
      id: undefined, hub: undefined }, toolSpecification: content.toolSpecification,
    resourceSpecification: content.resourceSpecification };
  }
  return { manifest: content.content,
    resources: content.resource };
}

/** Validate every ZIP destination before creating any output; preserve empty and binary resources. */
export async function agentSpecArchive(content: Record<string, unknown>): Promise<Blob> {
  if (typeof content.content !== 'string') throw new Error('Missing AgentSpec manifest');
  const resources = content.resource;
  if (resources != null && (typeof resources !== 'object' || Array.isArray(resources))) throw new Error('Invalid AgentSpec resource map');
  const files: { name: string; content: string; base64: boolean }[] = [];
  const names = new Set(['manifest.json']);
  for (const value of Object.values(resources ?? {})) {
    if (!value || typeof value !== 'object') throw new Error('Invalid AgentSpec resource');
    const resource = value as Record<string, unknown>;
    if (typeof resource.name !== 'string' || !resource.name || typeof resource.content !== 'string' ||
        resource.name.includes('\\') || resource.name.includes('\0') || resource.name.startsWith('/') ||
        /^[A-Za-z]:/.test(resource.name) || resource.name.split('/').some(part => part === '..' || part === '.' || !part)) {
      throw new Error('Unsafe AgentSpec archive path');
    }
    const identity = resource.name.toLowerCase();
    if (names.has(identity)) throw new Error('Conflicting AgentSpec archive paths');
    names.add(identity);
    const metadata = resource.metadata as Record<string, unknown> | null;
    const base64 = metadata?.encoding === 'base64';
    if (base64) atob(resource.content);
    files.push({ name: resource.name, content: resource.content, base64 });
  }
  const zip = new JSZip();
  zip.file('manifest.json', content.content);
  files.forEach(file => zip.file(file.name, file.content, { base64: file.base64 }));
  return zip.generateAsync({ type: 'blob' });
}

export function resourceDownloadName(detail: SharedResource): string {
  // Remove filesystem-forbidden characters, including control characters.
  // eslint-disable-next-line no-control-regex
  const clean = `${detail.resourceName}-${detail.version}`.replace(/[\\/:*?"<>|\x00-\x1f]/g, '_');
  return `${clean}.${detail.resourceType === 'agentspec' ? 'zip' : detail.resourceType === 'prompt' ? 'md' : 'json'}`;
}
