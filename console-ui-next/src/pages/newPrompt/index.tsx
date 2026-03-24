import { useEffect, useState, useCallback, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Editor } from '@monaco-editor/react';
import {
  ArrowLeft,
  MessageSquare,
  Variable,
  Sparkles,
  X,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { useNamespaceStore } from '@/stores/namespace-store';
import { promptApi } from '@/api/prompt';
import type { PromptVersionInfo, PromptMetaInfo } from '@/types/prompt';

// Extract {{variable}} from template
function extractVariables(template: string): string[] {
  if (!template) return [];
  const regex = /\{\{([^\s{}]+)\}\}/g;
  const variables: string[] = [];
  let match;
  while ((match = regex.exec(template)) !== null) {
    if (!variables.includes(match[1])) {
      variables.push(match[1]);
    }
  }
  return variables;
}

// Validate semver format
function isValidVersion(version: string): boolean {
  return /^\d+\.\d+\.\d+$/.test(version);
}

// Compare semver: return true if a > b
function isVersionGreater(a: string, b: string): boolean {
  if (!b) return true;
  const pa = a.split('.').map(Number);
  const pb = b.split('.').map(Number);
  for (let i = 0; i < 3; i++) {
    if (pa[i] > pb[i]) return true;
    if (pa[i] < pb[i]) return false;
  }
  return false;
}

// Suggest next patch version
function suggestNextVersion(current: string): string {
  if (!current) return '1.0.0';
  const parts = current.split('.');
  if (parts.length !== 3) return '1.0.0';
  const [major, minor, patch] = parts.map(Number);
  return `${major}.${minor}.${patch + 1}`;
}

type PageMode = 'create' | 'version';

export default function NewPromptPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentNamespace } = useNamespaceStore();

  const mode = (searchParams.get('mode') || 'create') as PageMode;
  const editPromptKey = searchParams.get('promptKey') || '';
  const namespaceId = searchParams.get('namespaceId') || currentNamespace || 'public';

  const isCreate = mode === 'create';
  const isVersion = mode === 'version';

  const [initLoading, setInitLoading] = useState(isVersion);
  const [saving, setSaving] = useState(false);

  // Form fields
  const [promptKey, setPromptKey] = useState('');
  const [version, setVersion] = useState('1.0.0');
  const [description, setDescription] = useState('');
  const [commitMsg, setCommitMsg] = useState('');
  const [template, setTemplate] = useState('');
  const [currentVersion, setCurrentVersion] = useState('');

  // Tags
  const [bizTags, setBizTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');

  // Variables extracted from template
  const [variableDefaults, setVariableDefaults] = useState<Record<string, string>>({});
  const [variableDescriptions, setVariableDescriptions] = useState<Record<string, string>>({});

  const variables = useMemo(() => extractVariables(template), [template]);

  const pageTitle = isCreate ? t('prompt.createPrompt') : t('prompt.publishVersion');

  // Load existing prompt data for version mode
  useEffect(() => {
    if (isCreate || !editPromptKey) {
      setInitLoading(false);
      return;
    }

    const loadData = async () => {
      setInitLoading(true);
      try {
        const metaRes = await promptApi.getPromptMetadata({ promptKey: editPromptKey, namespaceId });
        const meta = (metaRes as unknown as { data: PromptMetaInfo }).data;

        const detailRes = await promptApi.getPromptDetail({ promptKey: editPromptKey, namespaceId });
        const detail = (detailRes as unknown as { data: PromptVersionInfo }).data;

        setPromptKey(editPromptKey);
        setDescription(meta.description || '');
        setBizTags(meta.bizTags || []);
        setCurrentVersion(meta.latestVersion || '');
        setTemplate(detail.template || '');

        const defaults: Record<string, string> = {};
        const descs: Record<string, string> = {};
        (detail.variables || []).forEach((v) => {
          if (v.defaultValue) defaults[v.name] = v.defaultValue;
          if (v.description) descs[v.name] = v.description;
        });
        setVariableDefaults(defaults);
        setVariableDescriptions(descs);
        setVersion(suggestNextVersion(meta.latestVersion || ''));
      } catch {
        toast.error(t('prompt.loadFailed'));
      } finally {
        setInitLoading(false);
      }
    };

    loadData();
  }, [editPromptKey, namespaceId, isCreate, t]);

  const handleAddTag = useCallback(() => {
    const tag = tagInput.trim();
    if (!tag) return;
    if (bizTags.includes(tag)) {
      setTagInput('');
      return;
    }
    setBizTags((prev) => [...prev, tag]);
    setTagInput('');
  }, [tagInput, bizTags]);

  const handleRemoveTag = useCallback((tag: string) => {
    setBizTags((prev) => prev.filter((t) => t !== tag));
  }, []);

  const handleSubmit = async () => {
    if (isCreate && !promptKey.trim()) {
      toast.error(t('prompt.keyRequired'));
      return;
    }
    if (isCreate && !/^[a-zA-Z0-9_.-]+$/.test(promptKey.trim())) {
      toast.error(t('prompt.keyInvalid'));
      return;
    }
    if (!version.trim()) {
      toast.error(t('prompt.versionRequired'));
      return;
    }
    if (!isValidVersion(version.trim())) {
      toast.error(t('prompt.versionInvalid'));
      return;
    }
    if (isVersion && !isVersionGreater(version.trim(), currentVersion)) {
      toast.error(t('prompt.versionMustGreater', { current: currentVersion }));
      return;
    }
    if (!template.trim()) {
      toast.error(t('prompt.templateRequired'));
      return;
    }

    setSaving(true);
    try {
      const variablesDef = variables.map((name) => ({
        name,
        defaultValue: variableDefaults[name] || null,
        description: variableDescriptions[name] || null,
      }));

      await promptApi.publishVersion({
        promptKey: isCreate ? promptKey.trim() : editPromptKey,
        version: version.trim(),
        template: template,
        commitMsg: commitMsg.trim() || undefined,
        description: description.trim() || undefined,
        bizTags: bizTags.length > 0 ? bizTags.join(',') : undefined,
        variables: variablesDef.length > 0 ? JSON.stringify(variablesDef) : undefined,
        namespaceId,
      });
      toast.success(isCreate ? t('prompt.createSuccess') : t('prompt.publishSuccess'));
      navigate('/promptManagement');
    } catch {
      // handled by interceptor
    } finally {
      setSaving(false);
    }
  };

  if (initLoading) {
    return (
      <div className="space-y-5">
        <Skeleton className="h-10 w-64" />
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
          <div className="lg:col-span-2"><Skeleton className="h-[500px] w-full rounded-xl" /></div>
          <div><Skeleton className="h-[300px] w-full rounded-xl" /></div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col min-h-[calc(100vh-120px)]">
      <div className="space-y-5 grow">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2"
            onClick={() => navigate(-1)}
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            {t('prompt.backToList')}
          </Button>
          <Separator orientation="vertical" className="h-5" />
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-amber-500 to-orange-400">
              <MessageSquare className="h-4 w-4 text-white" />
            </div>
            <h1 className="text-lg font-bold">{pageTitle}</h1>
          </div>
        </div>
      </div>

      {/* Two-column layout */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Left column (2/3) - Form and Editor */}
        <div className="lg:col-span-2 space-y-5">
          {/* Basic Info Card */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <MessageSquare className="h-4 w-4 text-muted-foreground" />
                {t('prompt.basicInfo')}
              </h2>
            </div>
            <CardContent className="p-5">
              <div className={`flex flex-col ${isCreate ? 'gap-4' : 'gap-2'}`}>
              {/* Prompt Key + Version */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="sm:col-span-2 space-y-2.5">
                  <Label>{t('prompt.promptKey')} <span className="text-destructive">*</span></Label>
                  <Input
                    value={promptKey}
                    onChange={(e) => setPromptKey(e.target.value)}
                    placeholder={t('prompt.keyPlaceholder')}
                    disabled={isVersion}
                    className="bg-transparent"
                  />
                </div>
                <div className="space-y-2.5">
                  <Label>
                    {isVersion ? t('prompt.newVersion') : t('prompt.version')}
                    <span className="text-destructive"> *</span>
                    {isVersion && currentVersion && (
                      <span className="text-[11px] font-normal text-muted-foreground ml-1.5">
                        ({t('prompt.currentVersion')}: {currentVersion})
                      </span>
                    )}
                  </Label>
                  <Input
                    value={version}
                    onChange={(e) => setVersion(e.target.value)}
                    placeholder={isVersion ? t('prompt.newVersionPlaceholder') : t('prompt.versionPlaceholder')}
                    className="bg-transparent"
                  />
                </div>
              </div>

              {/* Description - only show in create mode */}
              {isCreate && (
              <div className="space-y-2.5">
                <Label>{t('prompt.description')}</Label>
                <Textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder={t('prompt.descriptionPlaceholder')}
                  rows={2}
                  className="bg-transparent"
                />
              </div>
              )}

              {/* Commit Message */}
              <div className="space-y-2.5">
                <Label>{t('prompt.commitMsg')}</Label>
                <Input
                  value={commitMsg}
                  onChange={(e) => setCommitMsg(e.target.value)}
                  placeholder={t('prompt.commitMsgPlaceholder')}
                  className="bg-transparent"
                />
              </div>

              {/* Tags - only show in create mode */}
              {isCreate && (
              <div className="space-y-2.5">
                <Label>{t('prompt.bizTags')}</Label>
                <div className="flex flex-wrap gap-1.5 mb-2">
                  {bizTags.map((tag) => (
                    <Badge key={tag} variant="secondary" className="gap-1 pr-1">
                      {tag}
                      <button
                        onClick={() => handleRemoveTag(tag)}
                        className="ml-0.5 rounded-full hover:bg-muted-foreground/20 p-0.5"
                      >
                        <X className="h-2.5 w-2.5" />
                      </button>
                    </Badge>
                  ))}
                </div>
                <div className="flex gap-2">
                  <Input
                    value={tagInput}
                    onChange={(e) => setTagInput(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), handleAddTag())}
                    placeholder={t('prompt.tagPlaceholder')}
                    className="bg-transparent flex-1"
                  />
                </div>
              </div>
              )}
              </div>
            </CardContent>
          </Card>

          {/* Template Editor Card */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30 flex items-center justify-between">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-amber-500" />
                {t('prompt.templateEditor')}
              </h2>
            </div>
            <CardContent className="p-0">
              <div className="border-b">
                <Editor
                  height="350px"
                  language="plaintext"
                  value={template}
                  theme="vs"
                  options={{
                    minimap: { enabled: false },
                    lineNumbers: 'on',
                    wordWrap: 'on',
                    scrollBeyondLastLine: false,
                    automaticLayout: true,
                    fontSize: 13,
                    tabSize: 2,
                  }}
                  onChange={(value) => setTemplate(value || '')}
                  loading={
                    <div className="flex items-center justify-center h-[350px] text-muted-foreground text-sm">
                      Loading editor...
                    </div>
                  }
                />
              </div>
              <div className="px-4 py-2.5">
                <p className="text-[11px] text-muted-foreground">
                  {t('prompt.templatePlaceholder')}
                </p>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Right column (1/3) - Variables Panel */}
        <div className="space-y-5">
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Variable className="h-4 w-4 text-amber-500" />
                {t('prompt.variables')}
                {variables.length > 0 && (
                  <Badge variant="secondary" className="ml-1 h-5 text-[10px] px-1.5">
                    {variables.length}
                  </Badge>
                )}
              </h2>
            </div>
            <CardContent className="p-4">
              {variables.length > 0 ? (
                <div className="space-y-3">
                  {variables.map((variable) => (
                    <div key={variable} className="space-y-1.5 rounded-lg border p-3">
                      <div className="flex items-center gap-1.5">
                        <span className="text-xs font-mono font-medium text-amber-600 dark:text-amber-400">
                          {`{{${variable}}}`}
                        </span>
                      </div>
                      <Input
                        value={variableDefaults[variable] || ''}
                        onChange={(e) =>
                          setVariableDefaults((prev) => ({ ...prev, [variable]: e.target.value }))
                        }
                        placeholder={t('prompt.variableDefault')}
                        className="h-7 text-xs bg-transparent"
                      />
                      <Input
                        value={variableDescriptions[variable] || ''}
                        onChange={(e) =>
                          setVariableDescriptions((prev) => ({ ...prev, [variable]: e.target.value }))
                        }
                        placeholder={t('prompt.variableDescription')}
                        className="h-7 text-xs bg-transparent"
                      />
                    </div>
                  ))}
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center py-8 text-muted-foreground">
                  <Variable className="h-8 w-8 text-muted-foreground/30 mb-2" />
                  <p className="text-xs">{t('prompt.noVariables')}</p>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      </div>

      {/* Sticky Bottom Toolbar */}
      <div className="sticky bottom-0 z-10 -mx-6 -mb-6 mt-2">
        <div className="border-t bg-background/95 backdrop-blur-sm py-3 px-6">
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={() => navigate(-1)} disabled={saving}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSubmit} disabled={saving}>
              {saving
                ? t('common.loading')
                : isCreate
                  ? t('prompt.createPrompt')
                  : t('prompt.publishVersion')}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
