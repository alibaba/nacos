import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft } from 'lucide-react';
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
import { MonacoEditor } from '@/components/config/MonacoEditor';
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
      toast.error(t('config.publishFailed'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCancel = () => {
    navigate('/configurationManagement');
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Button variant="outline" size="icon" onClick={handleCancel}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <h1 className="text-2xl font-semibold">{t('config.newConfig')}</h1>
      </div>

      {/* Form Card */}
      <Card>
        <CardHeader>
          <CardTitle>{t('config.newConfig')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6 pt-0">
          {/* Data ID and Group - 2 columns */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="space-y-2">
              <Label htmlFor="dataId">
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
            <div className="space-y-2">
              <Label htmlFor="group">
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
          </div>

          {/* Description */}
          <div className="space-y-2">
            <Label htmlFor="desc">{t('config.description')}</Label>
            <Textarea
              id="desc"
              value={desc}
              onChange={(e) => setDesc(e.target.value)}
              placeholder={t('config.description')}
              rows={2}
            />
          </div>

          {/* App Name and Tags - 2 columns */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
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
                placeholder="tag1,tag2,tag3"
              />
            </div>
          </div>

          {/* Config Type */}
          <div className="space-y-2">
            <Label htmlFor="type">{t('config.type')}</Label>
            <Select value={type} onValueChange={(value) => setType(value as ConfigType)}>
              <SelectTrigger id="type" className="w-full md:w-[280px]">
                <SelectValue />
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

          {/* Config Content */}
          <div className="space-y-2">
            <Label htmlFor="content">
              {t('config.content')}
              <span className="text-destructive ml-1">*</span>
            </Label>
            <MonacoEditor
              value={content}
              onChange={setContent}
              language={type}
              height="400px"
            />
          </div>
        </CardContent>
      </Card>

      {/* Footer Actions */}
      <div className="flex items-center justify-end gap-4">
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
