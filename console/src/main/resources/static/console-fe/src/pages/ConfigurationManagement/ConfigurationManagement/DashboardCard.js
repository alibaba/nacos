/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
import { ConfigProvider, Slider } from '@alifd/next';

@ConfigProvider.config
class DashboardCard extends React.Component {
  static displayName = 'DashboardCard';

  static propTypes = {
    locale: PropTypes.object,
    data: PropTypes.object,
    height: PropTypes.number,
  };

  render() {
    const { data = {}, height, locale = {} } = this.props;
    return (
      <div>
        {data.modeType === 'notice' ? (
          <div data-spm-click={'gostr=/aliyun;locaid=notice'}>
            <Slider style={{ marginBottom: data.modeList.length > 1 ? 20 : 10 }} arrows={false}>
              {data.modeList.map((item, index) => (
                <div key={index} className={'slider-img-wrapper'}>
                  <div
                    className={'alert alert-success'}
                    style={{ minHeight: 120, backgroundColor: '#e9feff' }}
                  >
                    <div className={'alert-success-text'} style={{ fontWeight: 'bold' }}>
                      {locale.importantReminder0}
                    </div>
                    <strong style={{ color: '#777a7e' }}>
                      <span>{item.title}</span>
                    </strong>
                    <strong>
                      <span>
                        {/* eslint-disable */}
                        <a
                          style={{ marginLeft: 10, color: '#33cde5' }}
                          href={item.url}
                          target="_blank"
                        >
                          {locale.viewDetails1}
                        </a>
                      </span>
                    </strong>
                  </div>
                </div>
              ))}
            </Slider>{' '}
          </div>
        ) : (
          <div
            className={'dash-card-contentwrappers'}
            style={{ height: height || 'auto' }}
            data-spm-click={`gostr=/aliyun;locaid=${data.modeType}`}
          >
            <h3 className={'dash-card-title'}>{data.modeName}</h3>
            <div className={'dash-card-contentlist'}>
              {data.modeList
                ? data.modeList.map(item => (
                    <div className={'dash-card-contentitem'}>
                      <a href={item.url} target={'_blank'}>
                        {item.title}
                      </a>
                      {item.tag === 'new' ? (
                        <img
                          style={{ width: 28, marginLeft: 2, verticalAlign: 'text-bottom' }}
                          src={'//img.alicdn.com/tps/TB1pS2YMVXXXXcCaXXXXXXXXXXX-56-24.png'}
                          alt=""
                        />
                      ) : (
                        ''
                      )}
                      {item.tag === 'hot' ? (
                        <img
                          style={{ width: 28, marginLeft: 2, verticalAlign: 'text-bottom' }}
                          src={'//img.alicdn.com/tps/TB1nusxPXXXXXb0aXXXXXXXXXXX-56-24.png'}
                          alt=""
                        />
                      ) : (
                        ''
                      )}
                    </div>
                  ))
                : ''}
            </div>
          </div>
        )}{' '}
      </div>
    );
  }
}

export default DashboardCard;
