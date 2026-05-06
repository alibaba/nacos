import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const DETAIL_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../index.tsx'),
  'utf-8',
);

const ZH_LOCALE_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../../../locales/zh-CN.json'),
  'utf-8',
);

const EN_LOCALE_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../../../locales/en-US.json'),
  'utf-8',
);

describe('AgentSpec detail basic info content', () => {
  it('uses the current version lifecycle status for the status field', () => {
    expect(DETAIL_SOURCE).toContain("value={<StatusBadge status={currentVersionStatus} label={currentVersionStatusLabel} />}");
    expect(DETAIL_SOURCE).toContain('t(`agentSpec.versionStatus.${currentVersionStatus}`)');
    expect(DETAIL_SOURCE).toContain("t('agentSpec.author')");
    expect(DETAIL_SOURCE).toContain("t('agentSpec.versionDownloads')");
  });

  it('guards the fallback detail reload so the page does not loop on missing version responses', () => {
    expect(DETAIL_SOURCE).toContain('if (!currentDetail || detailLoading || selectedVersion) {');
    expect(DETAIL_SOURCE).toContain('const fallbackVersion = sortVersionsDescending(currentDetail.versions || [])[0]?.version;');
    expect(DETAIL_SOURCE).toContain('if (fallbackVersion) {');
    expect(DETAIL_SOURCE).toContain('setSelectedVersion(fallbackVersion);');
  });

  it('falls back to getVersion for resource content when detail.agentSpec is absent', () => {
    expect(DETAIL_SOURCE).toContain("const [detailDocument, setDetailDocument] = useState<AgentSpecDocument | null>(null);");
    expect(DETAIL_SOURCE).toContain('agentSpecApi.getVersion({');
    expect(DETAIL_SOURCE).toContain('setDetailDocument(response.data);');
    expect(DETAIL_SOURCE).toContain('const spec = detailDocument ?? {');
  });

  it('renders the status field above the author field in the basic info card', () => {
    expect(DETAIL_SOURCE.indexOf("label={t('agentSpec.status')}")).toBeLessThan(
      DETAIL_SOURCE.indexOf("label={t('agentSpec.author')}"),
    );
  });

  it('does not render namespace or online status inside the basic info card', () => {
    const basicInfoSection = DETAIL_SOURCE.slice(
      DETAIL_SOURCE.indexOf('{/* Basic info card */}'),
      DETAIL_SOURCE.indexOf('{/* Resource viewer card */}'),
    );

    expect(basicInfoSection).not.toContain("t('agentSpec.namespace')");
    expect(basicInfoSection).not.toContain("t('agentSpec.onlineCountLabel')");
  });

  it('uses the revised online-version copy in Chinese locale', () => {
    expect(ZH_LOCALE_SOURCE).toContain('"onlineCount": "已上线版本数：{{count}}"');
    expect(DETAIL_SOURCE).toContain("const onlineVersionCountLabel = t('agentSpec.onlineCount', { count: detail.onlineCnt ?? 0 });");
  });

  it('renders labels as read-only content and keeps the page on outer scrolling', () => {
    expect(DETAIL_SOURCE).not.toContain('<LabelEditor');
    expect(DETAIL_SOURCE).toContain('const currentVersionLabels = Object.entries(detail.labels || {}).filter(');
    expect(DETAIL_SOURCE).toContain('className="flex min-h-[calc(100vh-88px)] flex-col gap-5 pb-5"');
    expect(DETAIL_SOURCE).toContain('<TabsContent value="overview">');
    expect(DETAIL_SOURCE).toContain('<Markdown remarkPlugins={[remarkGfm]}>');
    expect(DETAIL_SOURCE).toContain('{agentsContent}');
    expect(DETAIL_SOURCE).toContain('className="flex min-h-[480px] flex-col overflow-hidden py-0 gap-0"');
    expect(DETAIL_SOURCE).toContain('className="h-full min-h-0"');
    expect(DETAIL_SOURCE).toContain("t('common.versionLabels.noLabels')");
  });

  it('defines noLabels in the AgentSpec locale bundle and does not prepend an extra v to versions', () => {
    expect(ZH_LOCALE_SOURCE).toContain('"noLabels": "暂无标签"');
    expect(EN_LOCALE_SOURCE).toContain('"noLabels": "No labels"');
    expect(DETAIL_SOURCE).toContain('{selectedVersion}');
    expect(DETAIL_SOURCE).not.toContain('v{selectedVersion}');
  });
});
