import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';

import { configApi } from '@/api/config';
import { useNamespaceStore } from '@/stores/namespace-store';
import { MonacoEditor } from '@/components/config/MonacoEditor';
import { DiffEditor } from '@/components/config/DiffEditor';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { CONFIG_TYPES, type ConfigType, type ConfigBetaInfo, type Config } from '@/types/config';

interface ConfigEditorSnapshot {
  dataId: string;
  groupName: string;
  content: string;
  desc: string;
  appName: string;
  configTags: string;
  type: ConfigType;
}

const isSameConfigEditorSnapshot = (
  current: ConfigEditorSnapshot,
  loaded: ConfigEditorSnapshot,
) => current.dataId === loaded.dataId
  && current.groupName === loaded.groupName
  && current.content === loaded.content
  && current.desc === loaded.desc
  && current.appName === loaded.appName
  && current.configTags === loaded.configTags
  && current.type === loaded.type;

const getConfigurationManagementPath = (namespaceId: string) => {
  const params = new URLSearchParams();
  if (namespaceId) {
    params.set('namespace', namespaceId);
  }
  const query = params.toString();
  return query ? `/configurationManagement?${query}` : '/configurationManagement';
};

const isConfigLoadAuthFailure = (error: unknown) => {
  const status = (error as { response?: { status?: number } }).response?.status;
  return status === 401 || status === 403;
};

export default function ConfigEditorPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { t } = useTranslation();
  const { currentNamespace, setNamespaceChangeGuard } = useNamespaceStore();

  const [loading, setLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);

  // Form fields
  const [dataId, setDataId] = useState('');
  const [groupName, setGroupName] = useState('');
  const [content, setContent] = useState('');
  const [desc, setDesc] = useState('');
  const [appName, setAppName] = useState('');
  const [configTags, setConfigTags] = useState('');
  const [type, setType] = useState<ConfigType>('text');

  // Beta state
  const [activeTab, setActiveTab] = useState('production');
  const [betaContent, setBetaContent] = useState('');
  const [betaIps, setBetaIps] = useState('');
  const [betaExists, setBetaExists] = useState(false);
  const [betaLoading, setBetaLoading] = useState(false);
  const [betaPublishing, setBetaPublishing] = useState(false);
  const [stopBetaDialogOpen, setStopBetaDialogOpen] = useState(false);
  const [loadedSnapshot, setLoadedSnapshot] = useState<ConfigEditorSnapshot | null>(null);
  const [publishConfirmOpen, setPublishConfirmOpen] = useState(false);

  const urlDataId = searchParams.get('dataId') || '';
  const urlGroup = searchParams.get('group') || '';
  const urlNamespace = searchParams.get('namespace') || currentNamespace;
  const isEditing = !!(urlDataId && urlGroup);

  const currentSnapshot = useMemo<ConfigEditorSnapshot>(() => ({
    dataId,
    groupName,
    content,
    desc,
    appName,
    configTags,
    type,
  }), [appName, configTags, content, dataId, desc, groupName, type]);

  const hasUnsavedChanges = loadedSnapshot !== null
    && !isSameConfigEditorSnapshot(currentSnapshot, loadedSnapshot);

  const clearEditorState = useCallback(() => {
    setDataId('');
    setGroupName('');
    setContent('');
    setDesc('');
    setAppName('');
    setConfigTags('');
    setType('text');
    setActiveTab('production');
    setBetaContent('');
    setBetaIps('');
    setBetaExists(false);
    setBetaLoading(false);
    setStopBetaDialogOpen(false);
    setLoadedSnapshot(null);
    setPublishConfirmOpen(false);
  }, []);

  const redirectToConfigList = useCallback((showMissingMessage = false) => {
    clearEditorState();
    if (showMissingMessage) {
      toast.error(t('config.notFoundInNamespace'));
    }
    navigate(getConfigurationManagementPath(urlNamespace), { replace: true });
  }, [clearEditorState, navigate, t, urlNamespace]);

  const loadConfig = useCallback(async () => {
    setLoading(true);
    try {
      const result = await configApi.get({
        dataId: urlDataId,
        groupName: urlGroup,
        namespaceId: urlNamespace,
      });
      // Response interceptor already unwraps response.data
      const config = (result as unknown as { data: Config }).data;
      if (config) {
        const nextSnapshot: ConfigEditorSnapshot = {
          dataId: config.dataId,
          groupName: config.groupName,
          content: config.content || '',
          desc: config.desc || '',
          appName: config.appName || '',
          configTags: config.configTags || '',
          type: config.type || 'text',
        };
        setDataId(nextSnapshot.dataId);
        setGroupName(nextSnapshot.groupName);
        setContent(nextSnapshot.content);
        setDesc(nextSnapshot.desc);
        setAppName(nextSnapshot.appName);
        setConfigTags(nextSnapshot.configTags);
        setType(nextSnapshot.type);
        setActiveTab('production');
        setBetaContent('');
        setBetaIps('');
        setBetaExists(false);
        setLoadedSnapshot(nextSnapshot);
      } else {
        redirectToConfigList(true);
      }
    } catch (error) {
      if (isConfigLoadAuthFailure(error)) {
        clearEditorState();
        return;
      }
      redirectToConfigList();
    } finally {
      setLoading(false);
    }
  }, [clearEditorState, redirectToConfigList, urlDataId, urlGroup, urlNamespace]);

  useEffect(() => {
    if (urlDataId && urlGroup) {
      loadConfig();
    }
  }, [loadConfig, urlDataId, urlGroup]);

  useEffect(() => {
    if (!isEditing) {
      setNamespaceChangeGuard(null);
      return () => setNamespaceChangeGuard(null);
    }

    setNamespaceChangeGuard(() => {
      if (!hasUnsavedChanges) {
        return true;
      }
      return window.confirm(t('config.unsavedNamespaceSwitchConfirm'));
    });
    return () => setNamespaceChangeGuard(null);
  }, [hasUnsavedChanges, isEditing, setNamespaceChangeGuard, t]);

  const loadBeta = async () => {
    setBetaLoading(true);
    try {
      const response = await configApi.getBeta({
        dataId: urlDataId,
        groupName: urlGroup,
        namespaceId: urlNamespace,
      });
      const result = response as unknown as { data: ConfigBetaInfo };
      if (result.data) {
        setBetaExists(true);
        setBetaContent(result.data.content || '');
        // Parse grayRule to get IPs
        try {
          const rule = JSON.parse(result.data.grayRule || '{}');
          setBetaIps(rule.expr || '');
        } catch {
          setBetaIps('');
        }
      }
    } catch {
      // 404 means no beta config - that's ok
      setBetaExists(false);
      setBetaContent(content); // Pre-fill with production content
      setBetaIps('');
    } finally {
      setBetaLoading(false);
    }
  };

  const handleTabChange = (tab: string) => {
    setActiveTab(tab);
    if (tab === 'beta' && isEditing) {
      loadBeta();
    }
  };

  const handleBack = () => {
    navigate(`/configdetail?dataId=${encodeURIComponent(urlDataId)}&group=${encodeURIComponent(urlGroup)}&namespace=${encodeURIComponent(urlNamespace)}`);
  };

  const handleCancel = () => {
    navigate(`/configdetail?dataId=${encodeURIComponent(urlDataId)}&group=${encodeURIComponent(urlGroup)}&namespace=${encodeURIComponent(urlNamespace)}`);
  };

  const publishConfig = async () => {
    setPublishing(true);
    try {
      await configApi.publish({
        dataId,
        groupName,
        content,
        desc,
        configTags,
        type,
        appName,
        namespaceId: urlNamespace,
      });
      toast.success(t('config.publishSuccess'));
      setPublishConfirmOpen(false);
      navigate(`/configdetail?dataId=${encodeURIComponent(dataId)}&group=${encodeURIComponent(groupName)}&namespace=${encodeURIComponent(urlNamespace)}`);
    } catch {
      toast.error(t('config.publishFailed'));
    } finally {
      setPublishing(false);
    }
  };

  const handleSubmit = async () => {
    if (!content.trim()) {
      toast.error(t('config.contentRequired'));
      return;
    }

    if (isEditing && loadedSnapshot && loadedSnapshot.content !== content) {
      setPublishConfirmOpen(true);
      return;
    }

    await publishConfig();
  };

  const handleBetaPublish = async () => {
    if (!betaContent.trim()) {
      toast.error(t('config.contentRequired'));
      return;
    }
    if (!betaIps.trim()) {
      toast.error(t('config.betaIpsPlaceholder'));
      return;
    }

    setBetaPublishing(true);
    try {
      await configApi.publishBeta(
        {
          dataId,
          groupName,
          content: betaContent,
          desc,
          configTags,
          type,
          appName,
          namespaceId: urlNamespace,
        },
        betaIps.trim()
      );
      toast.success(t('config.betaPublishSuccess'));
      setBetaExists(true);
    } catch {
      // Error toast handled by interceptor
    } finally {
      setBetaPublishing(false);
    }
  };

  const handleStopBeta = async () => {
    try {
      await configApi.stopBeta({
        dataId: urlDataId,
        groupName: urlGroup,
        namespaceId: urlNamespace,
      });
      toast.success(t('config.betaStopSuccess'));
      setBetaExists(false);
      setBetaContent(content);
      setBetaIps('');
      setStopBetaDialogOpen(false);
      setActiveTab('production');
    } catch {
      // Error toast handled by interceptor
    }
  };

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <Button variant="outline" size="icon" disabled>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div className="h-8 w-48 bg-accent animate-pulse rounded" />
        </div>
        <Card>
          <CardContent className="p-6 space-y-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="space-y-2">
                <div className="h-4 w-20 bg-accent animate-pulse rounded" />
                <div className="h-10 w-full bg-accent animate-pulse rounded" />
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Button variant="outline" size="icon" onClick={handleBack}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <h1 className="text-2xl font-semibold">{t('config.editConfig')}</h1>
      </div>

      {/* Form Card with Tabs */}
      <Card>
        <CardHeader>
          <CardTitle>{t('config.editConfig')}</CardTitle>
        </CardHeader>
        <CardContent className="p-6 pt-0 space-y-6">
          {/* Shared fields - always visible */}
          <div className="space-y-2">
            <Label>{t('config.dataId')}</Label>
            <div className="h-9 px-3 py-1 rounded-md border bg-muted text-muted-foreground text-sm flex items-center">
              {dataId}
            </div>
          </div>

          <div className="space-y-2">
            <Label>{t('config.group')}</Label>
            <div className="h-9 px-3 py-1 rounded-md border bg-muted text-muted-foreground text-sm flex items-center">
              {groupName}
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="desc">{t('config.description')}</Label>
            <Textarea
              id="desc"
              value={desc}
              onChange={(e) => setDesc(e.target.value)}
              placeholder={t('config.description')}
              rows={3}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="appName">{t('config.appName')}</Label>
            <Input
              id="appName"
              value={appName}
              onChange={(e) => setAppName(e.target.value)}
              placeholder={t('config.appName')}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="tags">{t('config.tags')}</Label>
            <Input
              id="tags"
              value={configTags}
              onChange={(e) => setConfigTags(e.target.value)}
              placeholder={t('config.tags')}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="type">{t('config.type')}</Label>
            <Select value={type} onValueChange={(value) => setType(value as ConfigType)}>
              <SelectTrigger id="type">
                <SelectValue placeholder={t('config.type')} />
              </SelectTrigger>
              <SelectContent>
                {CONFIG_TYPES.map((configType) => (
                  <SelectItem key={configType.value} value={configType.value}>
                    {configType.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Tabs for Production / Beta content */}
          {isEditing ? (
            <Tabs value={activeTab} onValueChange={handleTabChange}>
              <TabsList>
                <TabsTrigger value="production">{t('config.production')}</TabsTrigger>
                <TabsTrigger value="beta">{t('config.beta')}</TabsTrigger>
              </TabsList>

              <TabsContent value="production">
                <div className="space-y-2">
                  <Label>{t('config.content')}</Label>
                  <MonacoEditor
                    value={content}
                    onChange={setContent}
                    language={type}
                    height="350px"
                  />
                </div>
              </TabsContent>

              <TabsContent value="beta">
                {betaLoading ? (
                  <div className="space-y-4 py-4">
                    <div className="h-4 w-32 bg-accent animate-pulse rounded" />
                    <div className="h-[350px] bg-accent animate-pulse rounded" />
                  </div>
                ) : (
                  <div className="space-y-4">
                    {/* Beta IPs */}
                    <div className="space-y-2">
                      <Label>{t('config.betaIps')}</Label>
                      <Input
                        value={betaIps}
                        onChange={(e) => setBetaIps(e.target.value)}
                        placeholder={t('config.betaIpsPlaceholder')}
                        disabled={betaExists}
                      />
                    </div>

                    {/* Beta content editor */}
                    <div className="space-y-2">
                      <Label>{t('config.content')}</Label>
                      <MonacoEditor
                        value={betaContent}
                        onChange={setBetaContent}
                        language={type}
                        height="350px"
                      />
                    </div>
                  </div>
                )}
              </TabsContent>
            </Tabs>
          ) : (
            <div className="space-y-2">
              <Label>{t('config.content')}</Label>
              <MonacoEditor
                value={content}
                onChange={setContent}
                language={type}
                height="350px"
              />
            </div>
          )}
        </CardContent>
      </Card>

      {/* Footer Actions */}
      <div className="flex items-center justify-end gap-4">
        <Button variant="outline" onClick={handleCancel} disabled={publishing || betaPublishing}>
          {t('common.cancel')}
        </Button>

        {activeTab === 'beta' && isEditing ? (
          <>
            {betaExists && (
              <Button
                variant="destructive"
                onClick={() => setStopBetaDialogOpen(true)}
                disabled={betaPublishing}
              >
                {t('config.stopBeta')}
              </Button>
            )}
            <Button onClick={handleBetaPublish} disabled={betaPublishing}>
              {betaPublishing ? t('common.loading') : t('config.betaPublish')}
            </Button>
          </>
        ) : (
          <Button onClick={handleSubmit} disabled={publishing}>
            {publishing ? t('common.loading') : t('config.publish')}
          </Button>
        )}
      </div>

      {/* Stop Beta Confirmation Dialog */}
      <Dialog open={stopBetaDialogOpen} onOpenChange={setStopBetaDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('config.stopBeta')}</DialogTitle>
            <DialogDescription>{t('config.stopBetaConfirm')}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setStopBetaDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleStopBeta}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Publish Diff Confirmation Dialog */}
      <Dialog
        open={publishConfirmOpen}
        onOpenChange={(open) => {
          if (!publishing) {
            setPublishConfirmOpen(open);
          }
        }}
      >
        <DialogContent className="max-w-[90vw] w-[90vw]">
          <DialogHeader>
            <DialogTitle>{t('config.publishConfirmTitle')}</DialogTitle>
            <DialogDescription>{t('config.publishConfirmDescription')}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-3 rounded-md border bg-muted/40 p-4 text-sm md:grid-cols-3">
            <div>
              <div className="text-muted-foreground">{t('config.dataId')}</div>
              <div className="font-medium break-all">{dataId}</div>
            </div>
            <div>
              <div className="text-muted-foreground">{t('config.group')}</div>
              <div className="font-medium break-all">{groupName}</div>
            </div>
            <div>
              <div className="text-muted-foreground">{t('common.namespace')}</div>
              <div className="font-medium break-all">{urlNamespace || 'public'}</div>
            </div>
          </div>
          <div className="flex justify-between text-sm text-muted-foreground">
            <span>{t('config.currentPublishedContent')}</span>
            <span>{t('config.pendingPublishContent')}</span>
          </div>
          <DiffEditor
            original={loadedSnapshot?.content || ''}
            modified={content}
            language={type}
            height="500px"
          />
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setPublishConfirmOpen(false)}
              disabled={publishing}
            >
              {t('common.cancel')}
            </Button>
            <Button onClick={publishConfig} disabled={publishing}>
              {publishing ? t('common.loading') : t('config.publish')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
