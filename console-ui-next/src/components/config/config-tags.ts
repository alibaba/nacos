export const parseConfigTags = (value: string) => Array.from(new Set(
  value
    .split(/[,，]/)
    .map((tag) => tag.trim())
    .filter(Boolean),
));

export const serializeConfigTags = (tags: string[]) => parseConfigTags(tags.join(',')).join(',');
