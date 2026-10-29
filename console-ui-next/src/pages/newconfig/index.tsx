import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, ChevronDown, SlidersHorizontal } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import { MonacoEditor } from '@/components/config/MonacoEditor';
import { ConfigTagsInput } from '@/components/config/ConfigTagsInput';
import { configApi } from '@/api/config';
import { useNamespaceStore } from '@/stores/namespace-store';
import { CONFIG_TYPES, type ConfigType } from '@/types/config';

const INVALID_CHARS = /[@#$%^&*\s]+/g;

export default function NewConfigPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { currentNamespace } = useNamespaceStore();

  const [dataId, setDataId] = useState('');
  const [groupName, setGroupName] = useState('DEFAULT_GROUP');
  const [desc, setDesc] = useState('');
  const [appName, setAppName] = useState('');
  const [configTags, setConfigTags] = useState('');
  const [type, setType] = useState<ConfigType>('text');
  const [content, setContent] = useState('');
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const validateForm = (): boolean => {
    if (!dataId.trim()) {
      toast.error(t('config.dataIdRequired'));
      return false;
    }
    if (!groupName.trim()) {
      toast.error(t('config.groupRequired'));
      return false;
    }
    if (!content.trim()) {
      toast.error(t('config.contentRequired'));
      return false;
    }
    if (INVALID_CHARS.test(dataId)) {
      toast.error(t('config.noSpecialChars'));
      return false;
    }
    if (INVALID_CHARS.test(groupName)) {
      toast.error(t('config.noSpecialChars'));
      return false;
    }
    return true;
  };

  const handleSubmit = async () => {
    if (!validateForm()) return;

    setIsSubmitting(true);
    try {
      await configApi.publish({
        dataId: dataId.trim(),
        groupName: groupName.trim(),
        content: content,
        desc: desc.trim(),
        configTags: configTags.trim(),
        type,
        appName: appName.trim(),
        namespaceId: currentNamespace,
      });
      toast.success(t('config.publishSuccess'));
      navigate('/configurationManagement');
    } catch (error) {
      console.error('Failed to publish config:', error);
      toast.error(t('config.publishFailed'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCancel = () => {
    navigate('/configurationManagement');
  };

  return (
    <div className="flex min-h-[calc(100vh-6.5rem)] flex-col gap-4">
      {/* Header */}
      <div className="flex shrink-0 items-center gap-4">
        <Button variant="outline" size="icon" onClick={handleCancel}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <h1 className="text-2xl font-semibold">{t('config.newConfig')}</h1>
      </div>

      {/* Form Card */}
      <Card className="flex-1 gap-3 py-4">
        <CardHeader className="px-5">
          <CardTitle>{t('config.newConfig')}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-1 flex-col gap-4 px-5">
          {/* Data ID and Group */}
          <div className="grid gap-2 md:grid-cols-[4rem_minmax(0,1fr)] md:items-center">
            <Label htmlFor="dataId" className="md:text-right">
              {t('config.dataId')}
              <span className="text-destructive ml-1">*</span>
            </Label>
            <Input
              id="dataId"
              value={dataId}
              onChange={(e) => setDataId(e.target.value)}
              placeholder={t('config.dataId')}
            />
          </div>

          <div className="grid gap-2 md:grid-cols-[4rem_minmax(0,1fr)] md:items-center">
            <Label htmlFor="group" className="md:text-right">
              {t('config.group')}
              <span className="text-destructive ml-1">*</span>
            </Label>
            <Input
              id="group"
              value={groupName}
              onChange={(e) => setGroupName(e.target.value)}
              placeholder={t('config.group')}
            />
          </div>

          {/* Description */}
          <div className="grid gap-2 md:grid-cols-[4rem_minmax(0,1fr)] md:items-start">
            <Label htmlFor="desc" className="md:pt-2 md:text-right">
              {t('config.description')}
            </Label>
            <Textarea
              id="desc"
              value={desc}
              onChange={(e) => setDesc(e.target.value)}
              placeholder={t('config.description')}
              rows={2}
            />
          </div>

          {/* Config Type */}
          <div className="grid gap-2 md:grid-cols-[4rem_minmax(0,1fr)] md:items-center">
            <Label htmlFor="type" className="md:text-right">{t('config.type')}</Label>
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

          {/* App Name and Tags */}
          <Collapsible open={advancedOpen} onOpenChange={setAdvancedOpen}>
            <div className="rounded-lg border border-dashed bg-muted/20">
              <CollapsibleTrigger asChild>
                <Button
                  type="button"
                  variant="ghost"
                  className="h-auto w-full justify-between px-4 py-3 hover:bg-muted/50"
                >
                  <span className="flex items-center gap-2">
                    <SlidersHorizontal className="h-4 w-4 text-muted-foreground" />
                    <span>{t('config.advancedOptions')}</span>
                    <span className="hidden text-xs font-normal text-muted-foreground sm:inline">
                      {t('config.appName')} · {t('config.tags')}
                    </span>
                  </span>
                  <ChevronDown
                    className={`h-4 w-4 transition-transform ${advancedOpen ? 'rotate-180' : ''}`}
                  />
                </Button>
              </CollapsibleTrigger>
              <CollapsibleContent className="space-y-4 px-4 pb-4 pt-3">
                <div className="grid gap-2 md:grid-cols-[4rem_minmax(0,1fr)] md:items-center">
                  <Label htmlFor="appName" className="md:text-right">{t('config.appName')}</Label>
                  <Input
                    id="appName"
                    value={appName}
                    onChange={(e) => setAppName(e.target.value)}
                    placeholder={t('config.appName')}
                  />
                </div>

                <div className="grid gap-2 md:grid-cols-[4rem_minmax(0,1fr)] md:items-center">
                  <Label htmlFor="tags" className="md:text-right">{t('config.tags')}</Label>
                  <ConfigTagsInput
                    id="tags"
                    value={configTags}
                    onChange={setConfigTags}
                    placeholder={t('config.tagsInputPlaceholder')}
                  />
                </div>
              </CollapsibleContent>
            </div>
          </Collapsible>

          {/* Config Content */}
          <div className="flex flex-1 flex-col gap-2">
            <Label>
              {t('config.content')}
              <span className="text-destructive ml-1">*</span>
            </Label>
            <div className="relative min-h-[240px] flex-1">
              <MonacoEditor
                value={content}
                onChange={setContent}
                language={type}
                height="100%"
                className="absolute inset-0"
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Footer Actions */}
      <div className="flex shrink-0 items-center justify-end gap-4">
        <Button variant="outline" onClick={handleCancel}>
          {t('common.cancel')}
        </Button>
        <Button onClick={handleSubmit} disabled={isSubmitting}>
          {isSubmitting ? t('common.loading') : t('config.publish')}
        </Button>
      </div>
    </div>
  );
}
