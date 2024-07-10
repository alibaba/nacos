/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import './index.scss';
import { Button, ConfigProvider, Radio } from '@alifd/next';
import PageTitle from '../../components/PageTitle';
import { changeLanguage } from '@/reducers/locale';
import changeTheme from '../../theme';
import { connect } from 'react-redux';
import { LANGUAGE_KEY, THEME } from '../../constants';

const { Group: RadioGroup } = Radio;

@connect(state => ({ ...state.locale }), { changeLanguage, changeTheme })
@ConfigProvider.config
class SettingCenter extends React.Component {
  static displayName = 'SettingCenter';

  static propTypes = {
    locale: PropTypes.object,
    changeLanguage: PropTypes.func,
    changeTheme: PropTypes.func,
  };

  constructor(props) {
    super(props);
    const defaultTheme = localStorage.getItem(THEME);
    this.state = {
      theme: defaultTheme === 'dark' ? 'dark' : 'light',
      language: localStorage.getItem(LANGUAGE_KEY),
    };
  }

  newTheme(value) {
    this.setState({
      theme: value,
    });
  }

  newLanguage(value) {
    this.setState({
      language: value,
    });
  }

  submit() {
    const { changeLanguage, changeTheme } = this.props;
    const currentLanguage = this.state.language;
    const currentTheme = this.state.theme;
    changeLanguage(currentLanguage);
    changeTheme(currentTheme);
  }

  render() {
    const { locale = {} } = this.props;
    const themeList = [
      { value: 'light', label: locale.settingLight },
      { value: 'dark', label: locale.settingDark },
    ];
    const languageList = [
      { value: 'en-US', label: 'English' },
      { value: 'zh-CN', label: '中文' },
    ];
    return (
      <>
        <PageTitle title={locale.settingTitle} />
        <div className="setting-box">
          <div className="text-box">
            <div className="setting-checkbox">
              <div className="setting-span">{locale.settingTheme}</div>
              <RadioGroup
                dataSource={themeList}
                value={this.state.theme}
                onChange={this.newTheme.bind(this)}
              />
            </div>
            <div className="setting-checkbox">
              <div className="setting-span">{locale.settingLocale}</div>
              <RadioGroup
                dataSource={languageList}
                value={this.state.language}
                onChange={this.newLanguage.bind(this)}
              />
            </div>
          </div>
          <Button type="primary" onClick={this.submit.bind(this)}>
            {locale.settingSubmit}
          </Button>
        </div>
      </>
    );
  }
}

export default SettingCenter;
