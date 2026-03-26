/**
 * Strip YAML frontmatter (--- ... ---) from the beginning of a markdown string.
 */
export function stripFrontmatter(md: string): string {
  return md.replace(/^---[\s\S]*?---\s*/, '');
}
