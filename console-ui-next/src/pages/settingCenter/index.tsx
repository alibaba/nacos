import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import {
  Bot, Save, Eye, EyeOff, Loader2,
} from 'lucide-react';

import client from '@/api/client';
import { useServerStore } from '@/stores/server-store';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';

interface CopilotConfig {
  apiKey: string;
  provider: string;
  protocol: string;
  region: string;
  model: string;
  baseUrl: string;
}

interface ModelMetadata {
  modelId: string;
}

interface EndpointMetadata {
  region: string;
  baseUrl: string;
}

interface ProtocolMetadata {
  name: string;
  endpoints: EndpointMetadata[];
}

interface ProviderMetadata {
  name: string;
  defaultModel: string;
  defaultProtocol?: string;
  defaultRegion?: string;
  models: ModelMetadata[];
  protocols: ProtocolMetadata[];
}

const findBaseUrl = (provider: ProviderMetadata | undefined, protocol: string, region: string) =>
  provider?.protocols.find((item) => item.name === protocol)?.endpoints
    .find((item) => item.region === region)?.baseUrl || '';

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

  const [apiKey, setApiKey] = useState('');
  const [providers, setProviders] = useState<ProviderMetadata[]>([]);
  const [provider, setProvider] = useState('DashScope');
  const [protocol, setProtocol] = useState('');
  const [region, setRegion] = useState('');
  const [model, setModel] = useState('qwen-turbo');
  const [baseUrl, setBaseUrl] = useState('');

  const loadConfig = useCallback(async () => {
    setLoading(true);
    try {
      const [configResponse, providersResponse] = await Promise.all([
        client.get('v3/console/copilot/config'),
        client.get('v3/console/copilot/config/providers'),
      ]);
      const configBody = configResponse as unknown as { data: CopilotConfig };
      const providersBody = providersResponse as unknown as { data: ProviderMetadata[] };
      const config = configBody.data || ({} as CopilotConfig);
      const providerOptions = providersBody.data || [];
      const providerName = config.provider || 'DashScope';
      const providerMetadata = providerOptions.find((item) => item.name === providerName);
      const protocolName = config.protocol || providerMetadata?.defaultProtocol || '';
      const regionName = config.region || providerMetadata?.defaultRegion || '';
      setProviders(providerOptions);
      setApiKey(config.apiKey || '');
      setProvider(providerName);
      setProtocol(protocolName);
      setRegion(regionName);
      setModel(config.model || providerMetadata?.defaultModel || 'qwen-turbo');
      setBaseUrl(config.baseUrl || findBaseUrl(providerMetadata, protocolName, regionName));
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
      const config: CopilotConfig = {
        apiKey: apiKey.trim(),
        provider: provider || 'DashScope',
        protocol,
        region,
        model: model || 'qwen-turbo',
        baseUrl: baseUrl.trim(),
      };

      await client.post('v3/console/copilot/config', JSON.stringify(config), {
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

  const selectedProvider = providers.find((item) => item.name === provider);
  const selectedProtocol = selectedProvider?.protocols.find((item) => item.name === protocol);

  const handleProviderChange = (value: string) => {
    const metadata = providers.find((item) => item.name === value);
    const nextProtocol = metadata?.defaultProtocol || '';
    const nextRegion = metadata?.defaultRegion || '';
    setProvider(value);
    setProtocol(nextProtocol);
    setRegion(nextRegion);
    setModel(metadata?.defaultModel || 'qwen-turbo');
    setBaseUrl(findBaseUrl(metadata, nextProtocol, nextRegion));
  };

  const handleProtocolChange = (value: string) => {
    setProtocol(value);
    setBaseUrl(findBaseUrl(selectedProvider, value, region));
  };

  const handleRegionChange = (value: string) => {
    setRegion(value);
    setBaseUrl(findBaseUrl(selectedProvider, protocol, value));
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
                {Array.from({ length: 2 }).map((_, i) => (
                  <div key={i} className="flex flex-col gap-2.5">
                    <Skeleton className="h-4 w-24" />
                    <Skeleton className="h-10 w-full" />
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex flex-col gap-5">
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
                  <p className="text-xs text-muted-foreground">{t('settings.apiKeyHint')}</p>
                </div>

                {/* Provider */}
                <div className="space-y-2.5">
                  <Label>Provider</Label>
                  <Select value={provider} onValueChange={handleProviderChange}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select provider" />
                    </SelectTrigger>
                    <SelectContent>
                      {providers.map((item) => (
                        <SelectItem key={item.name} value={item.name}>{item.name}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {selectedProvider && selectedProvider.protocols.length > 0 && (
                  <div className="space-y-2.5">
                    <Label>Protocol</Label>
                    <Select value={protocol} onValueChange={handleProtocolChange}>
                      <SelectTrigger>
                        <SelectValue placeholder="Select protocol" />
                      </SelectTrigger>
                      <SelectContent>
                        {selectedProvider.protocols.map((item) => (
                          <SelectItem key={item.name} value={item.name}>{item.name}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}

                {selectedProtocol && (
                  <div className="space-y-2.5">
                    <Label>Region</Label>
                    <Select value={region} onValueChange={handleRegionChange}>
                      <SelectTrigger>
                        <SelectValue placeholder="Select region" />
                      </SelectTrigger>
                      <SelectContent>
                        {selectedProtocol.endpoints.map((item) => (
                          <SelectItem key={item.region} value={item.region}>{item.region}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}

                {/* Model */}
                <div className="space-y-2.5">
                  <Label>{t('settings.model')}</Label>
                  <Select value={model} onValueChange={setModel}>
                    <SelectTrigger>
                      <SelectValue placeholder={t('settings.modelPlaceholder')} />
                    </SelectTrigger>
                    <SelectContent>
                      {selectedProvider?.models.map((item) => (
                        <SelectItem key={item.modelId} value={item.modelId}>
                          {item.modelId}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {/* Base URL */}
                {selectedProtocol && (
                  <div className="space-y-2.5">
                    <Label>Base URL</Label>
                    <Input
                      placeholder={findBaseUrl(selectedProvider, protocol, region)}
                      value={baseUrl}
                      onChange={(e) => setBaseUrl(e.target.value)}
                    />
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
