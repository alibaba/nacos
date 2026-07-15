/**
 * Check if a file is a valid zip file by extension (.zip) or MIME type (application/zip).
 */
export function isValidZipFile(file: File): boolean {
  if (file.type === 'application/zip') return true;
  return file.name.toLowerCase().endsWith('.zip');
}
