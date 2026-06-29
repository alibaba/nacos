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

/**
 * Skill docs often use "=" blocks as prompt section titles:
 *
 * ========================
 * 1. Section
 * ========================
 *
 * Markdown interprets the closing line as a Setext H1 marker. Convert the
 * block to an ATX heading for preview only, leaving the saved content intact.
 */
export function prepareSkillMarkdownPreview(md: string): string {
  let fenceChar = '';
  let fenceLength = 0;
  const lines = (md || '').split(/\r?\n/);
  const result: string[] = [];

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    const trimmed = line.trim();
    const fenceMatch = trimmed.match(/^(`{3,}|~{3,})/);
    if (fenceMatch) {
      const marker = fenceMatch[1];
      const markerChar = marker[0];
      if (!fenceChar) {
        fenceChar = markerChar;
        fenceLength = marker.length;
      } else if (markerChar === fenceChar && marker.length >= fenceLength) {
        fenceChar = '';
        fenceLength = 0;
      }
      result.push(line);
      continue;
    }

    if (!fenceChar && /^={3,}$/.test(trimmed)) {
      const title = lines[i + 1]?.trim();
      const closing = lines[i + 2]?.trim();
      if (title && closing && /^={3,}$/.test(closing)) {
        result.push(`## ${title}`);
        i += 2;
        continue;
      }

      result.push(line.replace(/=/g, '\\='));
      continue;
    }

    result.push(line);
  }

  return result.join('\n');
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
