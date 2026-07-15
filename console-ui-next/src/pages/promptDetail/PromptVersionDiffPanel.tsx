import { useEffect, useMemo, useState, type ReactElement } from 'react';
import { DiffEditor } from '@monaco-editor/react';
import { useTranslation } from 'react-i18next';
import {
  ArrowRightLeft,
  FileText,
  GitCompareArrows,
} from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { promptApi } from '@/api/prompt';
import type { PromptVersionInfo, PromptVersionSummary } from '@/types/prompt';
import { comparePromptVersions } from './prompt-version-diff-utils';

interface PromptVersionDiffPanelProps {
  namespaceId: string;
  promptKey: string;
  versions: PromptVersionSummary[];
  selectedVersion: string;
}

interface LoadedVersions {
  key: string;
  before: PromptVersionInfo | null;
  after: PromptVersionInfo | null;
}

const DIFF_EDITOR_HEIGHT = 'clamp(360px, calc(100vh - 440px), 580px)';

export function PromptVersionDiffPanel({
  namespaceId,
  promptKey,
  versions,
  selectedVersion,
}: PromptVersionDiffPanelProps) {
  const { t } = useTranslation();
  const [beforeVersionChoice, setBeforeVersionChoice] = useState('');
  const [afterVersionChoice, setAfterVersionChoice] = useState('');
  const [loadedVersions, setLoadedVersions] = useState<LoadedVersions | null>(null);

  const versionNames = useMemo(() => versions.map((version) => version.version), [versions]);
  const selectedVersionAvailable = !!selectedVersion && versionNames.includes(selectedVersion);
  const beforeVersion = versionNames.includes(beforeVersionChoice) ? beforeVersionChoice : '';
  const afterVersion = versionNames.includes(afterVersionChoice)
    ? afterVersionChoice
    : selectedVersionAvailable
      ? selectedVersion
      : '';
  const versionsKey = beforeVersion && afterVersion ? `${beforeVersion}\n${afterVersion}` : '';
  const versionsReady = !!versionsKey && loadedVersions?.key === versionsKey;
  const beforeInfo = versionsReady ? loadedVersions.before : null;
  const afterInfo = versionsReady ? loadedVersions.after : null;
  const loading = !!versionsKey && !versionsReady;
  const sameVersion = beforeVersion === afterVersion;

  useEffect(() => {
    if (!promptKey || !beforeVersion || !afterVersion || !versionsKey) return;

    let cancelled = false;
    Promise.all([
      promptApi.getVersionDetail({ namespaceId, promptKey, version: beforeVersion }),
      promptApi.getVersionDetail({ namespaceId, promptKey, version: afterVersion }),
    ])
      .then(([beforeResponse, afterResponse]) => {
        if (cancelled) return;
        setLoadedVersions({
          key: versionsKey,
          before: beforeResponse.data,
          after: afterResponse.data,
        });
      })
      .catch(() => {
        if (cancelled) return;
        setLoadedVersions({ key: versionsKey, before: null, after: null });
      });

    return () => {
      cancelled = true;
    };
  }, [namespaceId, promptKey, beforeVersion, afterVersion, versionsKey]);

  const diff = useMemo(
    () => comparePromptVersions(beforeInfo, afterInfo),
    [beforeInfo, afterInfo],
  );

  return (
    <div className="space-y-4">
      <Card className="py-0 gap-0 overflow-hidden">
        <CardContent className="p-4">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
            <div className="grid gap-3 sm:grid-cols-[minmax(0,220px)_auto_minmax(0,220px)] sm:items-end">
              <VersionSelect
                label={t('prompt.diffBeforeVersion')}
                placeholder={t('prompt.diffSelectVersionPlaceholder')}
                value={beforeVersion}
                versions={versionNames}
                onChange={setBeforeVersionChoice}
              />
              <Button
                variant="outline"
                size="icon"
                className="h-9 w-9 sm:mb-0.5"
                onClick={() => {
                  setBeforeVersionChoice(afterVersion);
                  setAfterVersionChoice(beforeVersion);
                }}
                disabled={!beforeVersion || !afterVersion}
                aria-label={t('prompt.diffSwapVersions')}
              >
                <ArrowRightLeft className="h-4 w-4" />
              </Button>
              <VersionSelect
                label={t('prompt.diffAfterVersion')}
                placeholder={t('prompt.diffSelectVersionPlaceholder')}
                value={afterVersion}
                versions={versionNames}
                onChange={setAfterVersionChoice}
              />
            </div>
            <DiffCountBadge label={t('prompt.diffModifiedContent')} count={diff.modified ? 1 : 0} />
          </div>
        </CardContent>
      </Card>

      {!beforeVersion || !afterVersion ? (
        <EmptyDiff icon={<GitCompareArrows />} message={t('prompt.diffSelectVersions')} />
      ) : loading ? (
        <div className="space-y-3">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-[460px] w-full" />
        </div>
      ) : sameVersion ? (
        <EmptyDiff icon={<GitCompareArrows />} message={t('prompt.diffSameVersion')} />
      ) : !diff.modified ? (
        <EmptyDiff icon={<FileText />} message={t('prompt.diffNoChanges')} />
      ) : (
        <Card className="py-0 gap-0 overflow-hidden">
          <div className="flex items-center justify-between border-b bg-muted/30 px-4 py-3">
            <h2 className="min-w-0 truncate font-mono text-sm font-semibold">
              {t('prompt.diffTemplateFile')}
            </h2>
          </div>
          <CardContent className="p-0">
            <div
              key={versionsKey}
              className="overflow-hidden"
              style={{ height: DIFF_EDITOR_HEIGHT }}
            >
              <DiffEditor
                height="100%"
                language="plaintext"
                original={diff.beforeContent}
                modified={diff.afterContent}
                theme="vs"
                options={{
                  readOnly: true,
                  minimap: { enabled: false },
                  renderSideBySide: true,
                  scrollBeyondLastLine: false,
                  wordWrap: 'on',
                  automaticLayout: true,
                  fontSize: 13,
                  renderOverviewRuler: true,
                  overviewRulerLanes: 3,
                  scrollbar: {
                    verticalScrollbarSize: 10,
                    horizontalScrollbarSize: 10,
                  },
                }}
                loading={
                  <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
                    {t('agentSpec.editorLoading')}
                  </div>
                }
              />
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function VersionSelect({
  label,
  placeholder,
  value,
  versions,
  onChange,
}: {
  label: string;
  placeholder: string;
  value: string;
  versions: string[];
  onChange: (value: string) => void;
}) {
  return (
    <div className="space-y-1.5">
      <span className="text-xs text-muted-foreground">{label}</span>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger className="h-9">
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>
          {versions.map((version) => (
            <SelectItem key={version} value={version}>
              {version}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

function DiffCountBadge({ label, count }: { label: string; count: number }) {
  return (
    <Badge className="w-fit border-0 bg-blue-50 font-medium text-blue-700 dark:bg-blue-950/40 dark:text-blue-300">
      {label}: {count}
    </Badge>
  );
}

function EmptyDiff({ icon, message }: { icon: ReactElement; message: string }) {
  return (
    <div className="flex min-h-[320px] flex-col items-center justify-center gap-3 rounded-lg border bg-card text-muted-foreground">
      {icon}
      <p className="text-sm">{message}</p>
    </div>
  );
}
