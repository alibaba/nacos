import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { getLanguage } from '@/lib/storage';

import zhCN from './zh-CN.json';
import enUS from './en-US.json';
import ruRU from './ru-RU.json';
import { EN_US, FALLBACK_LANGUAGE, RU_RU, ZH_CN, type Language } from './languages';

const resources: Record<Language, { translation: Record<string, unknown> }> = {
  [ZH_CN]: {
    translation: zhCN,
  },
  [EN_US]: {
    translation: enUS,
  },
  [RU_RU]: {
    translation: ruRU,
  },
};

i18n.use(initReactI18next).init({
  resources,
  lng: getLanguage(),
  fallbackLng: FALLBACK_LANGUAGE,
  interpolation: {
    escapeValue: false,
  },
  react: {
    useSuspense: false,
  },
});

export default i18n;
