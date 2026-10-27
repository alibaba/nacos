/** Only these existing read-only routes allow a viewer without a login session. */
export function isPublicHubPath(pathname: string): boolean {
  return /^\/(?:skill\/shared\/(?:list|detail\/[1-9]\d*)|hub\/skills(?:\/[1-9]\d*)?)\/?$/.test(pathname);
}
