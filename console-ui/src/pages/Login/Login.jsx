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
import { Card, Form, Input, Message, ConfigProvider, Field, Button, Divider } from '@alifd/next';
import { withRouter } from 'react-router-dom';

import './index.scss';
import Header from '../../layouts/Header';
import PropTypes from 'prop-types';
import { login } from '../../reducers/base';
import request from '../../utils/request';

const FormItem = Form.Item;

@withRouter
@ConfigProvider.config
class Login extends React.Component {
  static displayName = 'Login';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);
    this.state = {
      oidcList: [],
    };
  }

  componentDidMount() {
    if (localStorage.getItem('token')) {
      const [baseUrl] = location.href.split('#');
      location.href = `${baseUrl}#/`;
    } else {
      this.handleOidcLogin();
    }
  }

  handleOidcLogin() {
    const qsParse = require('qs/lib/parse');

    const query = qsParse(location.search.slice(1));

    // oidc login will redirect to login page with token param in query
    if (query.token) {
      localStorage.setItem('token', query.token);

      const [baseUrl] = location.href.split('?');
      location.href = `${baseUrl}#/`;
    } else {
      this.fetchOIDCList().then(oidcProviders => {
        this.setState({
          oidcList: oidcProviders,
        });
      });
    }
  }

  fetchOIDCList() {
    return request({
      url: 'v1/auth/oidc/list',
      method: 'GET',
    }).then(res => {
      return res || [];
    });
  }

  handleSubmit = () => {
    const { locale = {} } = this.props;
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }
      login(values)
        .then(res => {
          localStorage.setItem('token', JSON.stringify(res));
          this.props.history.push('/');
        })
        .catch(() => {
          Message.error({
            content: locale.invalidUsernameOrPassword,
          });
        });
    });
  };

  onKeyDown = event => {
    // 'keypress' event misbehaves on mobile so we track 'Enter' key via 'keydown' event
    if (event.key === 'Enter') {
      event.preventDefault();
      event.stopPropagation();
      this.handleSubmit();
    }
  };

  render() {
    const { locale = {} } = this.props;

    return (
      <div className="home-page">
        <Header />
        <section
          className="top-section"
          style={{
            background: 'url(img/black_dot.png) repeat',
            backgroundSize: '14px 14px',
          }}
        >
          <div className="vertical-middle product-area">
            <img className="product-logo" src="img/nacos.png" />
            <p className="product-desc">
              an easy-to-use dynamic service discovery, configuration and service management
              platform for building cloud native applications
            </p>
          </div>
          <div className="animation animation1" />
          <div className="animation animation2" />
          <div className="animation animation3" />
          <div className="animation animation4" />
          <div className="animation animation5" />
          <Card className="login-panel" contentHeight="auto">
            <div className="login-header">{locale.login}</div>
            <div className="internal-sys-tip">
              <div>{locale.internalSysTip1}</div>
              <div>{locale.internalSysTip2}</div>
            </div>
            <Form className="login-form" field={this.field}>
              <FormItem>
                <Input
                  {...this.field.init('username', {
                    rules: [
                      {
                        required: true,
                        message: locale.usernameRequired,
                      },
                    ],
                  })}
                  placeholder={locale.pleaseInputUsername}
                  onKeyDown={this.onKeyDown}
                />
              </FormItem>
              <FormItem>
                <Input
                  htmlType="password"
                  placeholder={locale.pleaseInputPassword}
                  {...this.field.init('password', {
                    rules: [
                      {
                        required: true,
                        message: locale.passwordRequired,
                      },
                    ],
                  })}
                  onKeyDown={this.onKeyDown}
                />
              </FormItem>
              {this.state.oidcList.length > 0 && (
                <FormItem
                  style={{
                    marginBottom: 0,
                  }}
                >
                  {this.state.oidcList.map((oidcItem, index) => {
                    return (
                      <>
                        <Button
                          className="oidc-login-btn"
                          component="a"
                          href={`${location.href.split('#')[0]}v1/auth/oidc/init?oidpId=${
                            oidcItem.key
                          }`}
                          primary
                          text
                          key={oidcItem.key}
                        >
                          {oidcItem.name}
                        </Button>

                        {index !== this.state.oidcList.length - 1 && <Divider direction="ver" />}
                      </>
                    );
                  })}
                </FormItem>
              )}
              <FormItem label=" ">
                <Form.Submit className="submit-btn" onClick={this.handleSubmit}>
                  {locale.submit}
                </Form.Submit>
              </FormItem>
            </Form>
          </Card>
        </section>
      </div>
    );
  }
}

export default Login;
