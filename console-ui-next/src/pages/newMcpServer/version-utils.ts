export function nextMcpVersion(version: string): string {
  const semVer = /^(\d+)\.(\d+)\.(\d+)$/.exec(version);
  if (semVer) {
    return `${semVer[1]}.${semVer[2]}.${Number(semVer[3]) + 1}`;
  }
  const numeric = /^v(\d+)$/.exec(version);
  if (numeric) {
    return `v${Number(numeric[1]) + 1}`;
  }
  return version ? `${version}-next` : '';
}
