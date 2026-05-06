import type { AgentSpecResource } from '@/types/agentspec';

const MANIFEST_KEY = 'manifest.json';

export interface EditorFile {
  content: string;
  type: string; // resource type: config | skill | cron | dockerfile | other
}

/**
 * Serialize the editor file map into API-ready content + resource map.
 * - manifest.json → content (string)
 * - All other files → resource map keyed by name
 */
export function serializeFileTree(
  files: Map<string, EditorFile>,
): { content: string; resource: Record<string, AgentSpecResource> } {
  let content = '{}';
  const resource: Record<string, AgentSpecResource> = Object.create(null);

  for (const [key, file] of files) {
    if (key === MANIFEST_KEY) {
      content = file.content;
    } else {
      resource[key] = {
        name: key,
        type: file.type as AgentSpecResource['type'],
        content: file.content,
        metadata: null,
      };
    }
  }

  return { content, resource };
}

/**
 * Deserialize API content + resource map back into the editor file map.
 * - content → manifest.json entry
 * - Each resource → file entry keyed by resource name
 */
export function deserializeToFiles(
  content: string,
  resources: Record<string, AgentSpecResource>,
): Map<string, EditorFile> {
  const files = new Map<string, EditorFile>();

  files.set(MANIFEST_KEY, { content, type: 'manifest' });

  for (const key of Object.getOwnPropertyNames(resources)) {
    const resource = resources[key];
    files.set(resource.name, {
      content: resource.content,
      type: resource.type,
    });
  }

  return files;
}
