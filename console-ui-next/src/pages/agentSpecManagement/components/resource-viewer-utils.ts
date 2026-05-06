/**
 * Map a file name to a Monaco Editor language mode.
 * .json → json, .md → markdown, Dockerfile → dockerfile, others → plaintext
 */
export function getLanguageFromFileName(fileName: string): string {
  if (fileName === 'Dockerfile' || fileName.endsWith('/Dockerfile')) {
    return 'dockerfile';
  }
  const dotIdx = fileName.lastIndexOf('.');
  if (dotIdx === -1) return 'plaintext';
  const ext = fileName.slice(dotIdx).toLowerCase();
  switch (ext) {
    case '.json':
      return 'json';
    case '.md':
      return 'markdown';
    case '.js':
      return 'javascript';
    case '.ts':
      return 'typescript';
    case '.yaml':
    case '.yml':
      return 'yaml';
    case '.xml':
      return 'xml';
    case '.html':
      return 'html';
    case '.css':
      return 'css';
    case '.sh':
      return 'shell';
    default:
      return 'plaintext';
  }
}
