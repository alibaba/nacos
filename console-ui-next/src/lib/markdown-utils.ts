/**
 * Strip YAML frontmatter (--- ... ---) from the beginning of a markdown string.
 */
export function stripFrontmatter(md: string): string {
  return md.replace(/^---[\s\S]*?---\s*/, '');
}

/**
 * Returns true when markdown has non-empty body after removing frontmatter.
 */
export function hasNonFrontmatterMarkdownBody(md: string): boolean {
  return stripFrontmatter(md || '').trim().length > 0;
}

const FRONTMATTER_RE = /^---\r?\n([\s\S]*?)\r?\n---/;

/**
 * Parse simple key-value pairs from YAML frontmatter.
 * Handles only flat `key: value` lines (sufficient for skill.md metadata).
 */
export function parseFrontmatter(md: string): Record<string, string> {
  const match = md.match(FRONTMATTER_RE);
  if (!match) return {};
  const result: Record<string, string> = {};
  for (const line of match[1].split(/\r?\n/)) {
    const idx = line.indexOf(':');
    if (idx > 0) {
      const key = line.slice(0, idx).trim();
      let val = line.slice(idx + 1).trim();
      // strip surrounding quotes
      if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
        val = val.slice(1, -1);
      }
      result[key] = val;
    }
  }
  return result;
}

/**
 * Update (or insert) a field in the YAML frontmatter of a markdown string.
 * If no frontmatter exists, one is created.
 */
export function updateFrontmatterField(md: string, field: string, value: string): string {
  const match = md.match(FRONTMATTER_RE);
  if (!match) {
    // No frontmatter — prepend one
    return `---\n${field}: ${value}\n---\n\n${md}`;
  }
  const lines = match[1].split(/\r?\n/);
  let found = false;
  const updated = lines.map((line) => {
    const idx = line.indexOf(':');
    if (idx > 0 && line.slice(0, idx).trim() === field) {
      found = true;
      return `${field}: ${value}`;
    }
    return line;
  });
  if (!found) {
    // Insert at the beginning of frontmatter so name always appears first
    updated.unshift(`${field}: ${value}`);
  }
  return md.replace(FRONTMATTER_RE, `---\n${updated.join('\n')}\n---`);
}
