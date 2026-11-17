import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import {
  Bot, Save, Eye, EyeOff, Loader2, CheckCircle2, AlertTriangle,
} from 'lucide-react';

import client from '@/api/client';
import { useServerStore } from '@/stores/server-store';
import {
  buildCopilotConfigPayload,
  hasLegacyConnection,
  isConnectionUsable,
  isSelectionOverriddenByDeployment,
  type CopilotConfigView,
  type CopilotConnectionInfo,
} from '@/lib/copilot-config';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';

const LEGACY_MODELS = [
  { value: 'qwen-turbo', label: 'qwen-turbo', desc: 'Fast' },
  { value: 'qwen-plus', label: 'qwen-plus', desc: 'Enhanced' },
  { value: 'qwen-max', label: 'qwen-max', desc: 'Strongest' },
  { value: 'qwen-7b-chat', label: 'qwen-7b-chat', desc: '7B' },
  { value: 'qwen-14b-chat', label: 'qwen-14b-chat', desc: '14B' },
  { value: 'qwen-72b-chat', label: 'qwen-72b-chat', desc: '72B' },
  { value: 'qwen3-turbo', label: 'qwen3-turbo', desc: 'Qwen3 Fast' },
  { value: 'qwen3-plus', label: 'qwen3-plus', desc: 'Qwen3 Enhanced' },
  { value: 'qwen3-max', label: 'qwen3-max', desc: 'Qwen3 Strongest' },
  { value: 'qwen3-7b-instruct', label: 'qwen3-7b-instruct', desc: '7B' },
  { value: 'qwen3-14b-instruct', label: 'qwen3-14b-instruct', desc: '14B' },
  { value: 'qwen3-32b-instruct', label: 'qwen3-32b-instruct', desc: '32B' },
  { value: 'qwen3-72b-instruct', label: 'qwen3-72b-instruct', desc: '72B' },
];

function matchConnection(
  connections: CopilotConnectionInfo[],
  selection: string
): CopilotConnectionInfo | undefined {
  const normalized = selection.trim().toLowerCase();
  return connections.find((connection) => connection.name.toLowerCase() === normalized);
}

function connectionLabel(connection: CopilotConnectionInfo): string {
  return connection.displayName || connection.name;
}

function ConnectionOption({
  connection,
  active,
  labels,
}: {
  connection: CopilotConnectionInfo;
  active: boolean;
  labels: {
    unavailable: string;
    disabled: string;
    deployment: string;
    legacy: string;
    environment: string;
    deploymentCredential: string;
  };
}) {
  const usable = isConnectionUsable(connection);
  return (
    <label
      htmlFor={`copilot-connection-${connection.name}`}
      className="flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors hover:bg-accent/50"
    >
      <RadioGroupItem
        id={`copilot-connection-${connection.name}`}
        value={connection.name}
        disabled={!usable}
        className="mt-0.5"
      />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">{connectionLabel(connection)}</span>
          {connection.model && (
            <span className="text-xs text-muted-foreground">{connection.model}</span>
          )}
          <Badge variant="outline">
            {connection.source === 'LEGACY' ? labels.legacy : labels.deployment}
          </Badge>
          {active && <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />}
          {connection.enabled === false && (
            <Badge variant="destructive">{labels.disabled}</Badge>
          )}
          {connection.available === false && (
            <Badge variant="destructive">{labels.unavailable}</Badge>
          )}
          {connection.actualSource === 'ENVIRONMENT' && (
            <Badge variant="secondary">{labels.environment}</Badge>
          )}
          {connection.source === 'LEGACY' && connection.actualSource === 'DEPLOYMENT' && (
            <Badge variant="secondary">{labels.deploymentCredential}</Badge>
          )}
        </div>
        <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
          <span className="font-mono">{connection.name}</span>
          {connection.providerId && <span>{connection.providerId}</span>}
        </div>
        {connection.unavailableReason && (
          <p className="text-xs text-destructive">{connection.unavailableReason}</p>
        )}
      </div>
    </label>
  );
}

export default function SettingCenterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const copilotEnabled = useServerStore((s) => s.copilotEnabled);
  const stateLoaded = useServerStore((s) => s.stateLoaded);

  useEffect(() => {
    if (stateLoaded && !copilotEnabled) {
      navigate('/', { replace: true });
    }
  }, [stateLoaded, copilotEnabled, navigate]);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);

  const [config, setConfig] = useState<CopilotConfigView>({});
  const [selectedConnection, setSelectedConnection] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [model, setModel] = useState('qwen-turbo');

  const loadConfig = useCallback(async () => {
    setLoading(true);
    try {
      const response = await client.get('v3/console/copilot/config');
      const body = response as unknown as { data: CopilotConfigView };
      const next = body.data || {};
      const storedSelection = (next.selectedConnection ?? '').trim();
      const matched = matchConnection(next.connections ?? [], storedSelection);
      // A stored selection that can no longer serve requests falls back to automatic, so the
      // form never submits a connection the server would reject.
      setConfig(next);
      setSelectedConnection(
        matched && isConnectionUsable(matched) ? matched.name : ''
      );
      setApiKey(next.apiKey || '');
      setModel(next.model || 'qwen-turbo');
    } catch {
      // Error handled by interceptor
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (copilotEnabled) {
      loadConfig();
    }
  }, [copilotEnabled, loadConfig]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const payload = buildCopilotConfigPayload({
        selectedConnection,
        connections: config.connections,
        apiKey,
        model,
      });

      await client.post('v3/console/copilot/config', JSON.stringify(payload), {
        headers: { 'Content-Type': 'application/json' },
      });
      toast.success(t('settings.saveSuccess'));
      loadConfig();
    } catch {
      // Error handled by interceptor
    } finally {
      setSaving(false);
    }
  };

  const connections = config.connections ?? [];
  const legacyVisible = hasLegacyConnection(connections);
  const storedSelection = (config.selectedConnection ?? '').trim();
  const selectionKnown =
    !storedSelection || Boolean(matchConnection(connections, storedSelection));
  const activeConnection = config.activeConnection
    ? matchConnection(connections, config.activeConnection)
    : undefined;
  const modelOptions = LEGACY_MODELS.some((option) => option.value === model)
    ? LEGACY_MODELS
    : [{ value: model, label: model, desc: '' }, ...LEGACY_MODELS];

  const sourceLabels = {
    deployment: t('settings.sourceDeployment'),
    legacy: t('settings.sourceLegacy'),
    unavailable: t('settings.connectionUnavailable'),
    disabled: t('settings.connectionDisabled'),
    environment: t('settings.credentialEnvironment'),
    deploymentCredential: t('settings.credentialDeployment'),
  };

  return (
    <div className="flex flex-col gap-6 max-w-3xl">
      {/* Page Header */}
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t('settings.title')}</h1>
        <p className="text-sm text-muted-foreground mt-1">{t('settings.description')}</p>
      </div>

      {/* Copilot Config Card */}
      {copilotEnabled && (
        <Card className="py-0">
          <CardContent className="py-6">
            {/* Section Header */}
            <div className="flex items-start gap-3 mb-6">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                <Bot className="h-[18px] w-[18px] text-primary" />
              </div>
              <div>
                <h2 className="text-base font-semibold leading-none mt-0.5">{t('settings.copilotConfig')}</h2>
                <p className="text-sm text-muted-foreground mt-1.5">{t('settings.copilotConfigDesc')}</p>
              </div>
            </div>

            {loading ? (
              <div className="space-y-6">
                {Array.from({ length: 3 }).map((_, i) => (
                  <div key={i} className="flex flex-col gap-2.5">
                    <Skeleton className="h-4 w-24" />
                    <Skeleton className="h-10 w-full" />
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex flex-col gap-6">
                {/* Model Connection */}
                <div className="space-y-3">
                  <Label>{t('settings.connection')}</Label>
                  <RadioGroup
                    value={selectedConnection}
                    onValueChange={setSelectedConnection}
                    className="grid gap-2"
                  >
                    <label
                      htmlFor="copilot-connection-automatic"
                      className="flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors hover:bg-accent/50"
                    >
                      <RadioGroupItem
                        id="copilot-connection-automatic"
                        value=""
                        className="mt-0.5"
                      />
                      <div className="flex flex-col gap-1">
                        <span className="text-sm font-medium">{t('settings.connectionAutomatic')}</span>
                        <span className="text-xs text-muted-foreground">
                          {t('settings.connectionAutomaticDesc')}
                        </span>
                      </div>
                    </label>
                    {connections.map((connection) => (
                      <ConnectionOption
                        key={connection.name}
                        connection={connection}
                        active={
                          activeConnection?.name.toLowerCase() === connection.name.toLowerCase()
                        }
                        labels={sourceLabels}
                      />
                    ))}
                    {!selectionKnown && (
                      <div className="flex items-start gap-3 rounded-lg border border-dashed border-destructive/50 p-3">
                        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-destructive" />
                        <div className="flex flex-col gap-1">
                          <span className="text-sm font-medium">{storedSelection}</span>
                          <span className="text-xs text-muted-foreground">
                            {t('settings.connectionMissing')}
                          </span>
                        </div>
                      </div>
                    )}
                  </RadioGroup>
                  {isSelectionOverriddenByDeployment(config) && (
                    <div className="flex items-start gap-3 rounded-lg border border-yellow-600/40 bg-yellow-50/50 p-3 dark:bg-yellow-950/20">
                      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-yellow-600 dark:text-yellow-400" />
                      <span className="text-xs text-muted-foreground">
                        {t('settings.selectionOverriddenByDeployment', {
                          name: config.effectiveSelectedConnection || '',
                        })}
                      </span>
                    </div>
                  )}
                  <p className="text-xs text-muted-foreground">{t('settings.connectionDesc')}</p>

                  {/* Active connection */}
                  <div className="flex items-start gap-2 rounded-lg bg-muted/50 p-3 text-sm">
                    {activeConnection ? (
                      <>
                        <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
                        <span>
                          {t('settings.activeConnection')}
                          {': '}
                          <span className="font-medium">{connectionLabel(activeConnection)}</span>
                        </span>
                      </>
                    ) : (
                      <>
                        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-yellow-600 dark:text-yellow-400" />
                        <span className="text-muted-foreground">{t('settings.noActiveConnection')}</span>
                      </>
                    )}
                  </div>
                </div>

                {/* Legacy Credential */}
                {legacyVisible && (
                  <div className="space-y-4 rounded-lg border border-dashed p-4">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="text-sm font-semibold">{t('settings.legacySection')}</h3>
                      <Badge variant="secondary">{t('settings.deprecated')}</Badge>
                    </div>
                    <p className="text-xs text-muted-foreground">{t('settings.legacySectionDesc')}</p>

                    {/* API Key */}
                    <div className="space-y-2.5">
                      <Label>{t('settings.apiKey')}</Label>
                      <div className="relative">
                        <Input
                          type={showApiKey ? 'text' : 'password'}
                          placeholder={t('settings.apiKeyPlaceholder')}
                          value={apiKey}
                          onChange={(e) => setApiKey(e.target.value)}
                          className="pr-10"
                        />
                        <button
                          type="button"
                          onClick={() => setShowApiKey(!showApiKey)}
                          className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                        >
                          {showApiKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                        </button>
                      </div>
                      <p className="text-xs text-muted-foreground">{t('settings.legacyApiKeyHint')}</p>
                    </div>

                    {/* Model */}
                    <div className="space-y-2.5">
                      <Label>{t('settings.model')}</Label>
                      <Select value={model} onValueChange={setModel}>
                        <SelectTrigger>
                          <SelectValue placeholder={t('settings.modelPlaceholder')} />
                        </SelectTrigger>
                        <SelectContent>
                          {modelOptions.map((m) => (
                            <SelectItem key={m.value} value={m.value}>
                              <span>{m.label}</span>
                              {m.desc && (
                                <span className="ml-2 text-muted-foreground text-xs">({m.desc})</span>
                              )}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Save Action */}
            <div className="flex justify-end mt-6 pt-5 border-t">
              <Button onClick={handleSave} disabled={saving || loading} className="gap-2 min-w-[120px]">
                {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                {saving ? t('common.loading') : t('common.save')}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
