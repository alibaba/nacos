export function normalizeRelativePath(value: string): string {
  return value
    .replace(/\\/g, '/')
    .split('/')
    .map((segment) => segment.trim())
    .filter(Boolean)
    .join('/');
}

export function resolveCreateLocation(
  selectedType: string,
  path: string,
  customTypeValue: string,
  fallbackType = 'other',
): { resourceType: string; relativePath: string } {
  const normalizedPath = normalizeRelativePath(path);

  if (selectedType !== customTypeValue) {
    const duplicatedPrefix = `${selectedType}/`;
    return {
      resourceType: selectedType,
      relativePath: normalizedPath.startsWith(duplicatedPrefix)
        ? normalizedPath.slice(duplicatedPrefix.length)
        : normalizedPath,
    };
  }

  const segments = normalizedPath.split('/').filter(Boolean);

  if (segments.length === 1) {
    return {
      resourceType: fallbackType,
      relativePath: segments[0],
    };
  }

  const [resourceType, ...rest] = segments;

  return {
    resourceType: resourceType || '',
    relativePath: rest.join('/'),
  };
}

export function getAncestorFolders(path: string): string[] {
  const segments = normalizeRelativePath(path).split('/').filter(Boolean);
  const ancestors: string[] = [];
  for (let index = 1; index < segments.length; index += 1) {
    ancestors.push(segments.slice(0, index).join('/'));
  }
  return ancestors;
}