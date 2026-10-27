import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkFrontmatter from 'remark-frontmatter';
import { prepareSkillMarkdownPreview } from '@/lib/markdown-utils';

/** Shared Skills may contain untrusted HTML; keep the read-only preview inert. */
export function HubSkillMarkdown({ source }: { source: string }) {
  return <div className="app-markdown prose prose-sm dark:prose-invert max-w-none">
    <Markdown skipHtml remarkPlugins={[remarkGfm, remarkFrontmatter]}>
      {prepareSkillMarkdownPreview(source)}
    </Markdown>
  </div>;
}
