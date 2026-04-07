function normalizeDescription(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

export function getAgentSpecDescription(manifestContent: string): string {
  try {
    const manifest = JSON.parse(manifestContent) as Record<string, unknown>;
    if (!manifest || typeof manifest !== 'object' || Array.isArray(manifest)) {
      return '';
    }

    const rootDescription = normalizeDescription(manifest.description);
    if (rootDescription) {
      return rootDescription;
    }

    const worker = manifest.worker;
    if (worker && typeof worker === 'object' && !Array.isArray(worker)) {
      return normalizeDescription((worker as Record<string, unknown>).description);
    }
  } catch {
    // Keep editor flow permissive while manifest content is temporarily invalid JSON.
  }

  return '';
}

export function syncManifestDescription(manifestContent: string, description: string): string {
  const trimmedDescription = description.trim();

  try {
    const manifest = JSON.parse(manifestContent) as Record<string, unknown>;
    if (!manifest || typeof manifest !== 'object' || Array.isArray(manifest)) {
      return manifestContent;
    }

    const nextManifest: Record<string, unknown> = { ...manifest };

    if (trimmedDescription) {
      nextManifest.description = trimmedDescription;
    } else {
      delete nextManifest.description;
    }

    const worker = nextManifest.worker;
    if (worker && typeof worker === 'object' && !Array.isArray(worker)) {
      const nextWorker = { ...(worker as Record<string, unknown>) };
      if (trimmedDescription) {
        nextWorker.description = trimmedDescription;
      } else {
        delete nextWorker.description;
      }
      nextManifest.worker = nextWorker;
    }

    return JSON.stringify(nextManifest, null, 2);
  } catch {
    return manifestContent;
  }
}