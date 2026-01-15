/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
import { ConfigProvider, Message } from '@alifd/next';
import PageTitle from 'components/PageTitle';
import { getParams } from '@/globalLib';

@ConfigProvider.config
class PromptManagement extends React.Component {
  static displayName = 'PromptManagement';

  static propTypes = {
    locale: PropTypes.object,
  };

  render() {
    const { locale = {} } = this.props;
    return (
      <div className="prompt-management">
        <PageTitle
          title={locale.promptRegistry || 'Prompt Registry'}
          namespaceId={getParams('namespace') || 'public'}
        />
        <div style={{ padding: 40, textAlign: 'center' }}>
          <Message type="notice">
            {locale.promptRegistryComingSoon || 'Prompt Registry is coming soon...'}
          </Message>
        </div>
      </div>
    );
  }
}

export default PromptManagement;
