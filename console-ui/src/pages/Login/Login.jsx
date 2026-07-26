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
import { Button, Card, Form, Input, Message, ConfigProvider, Field } from '@alifd/next';
import { withRouter } from 'react-router-dom';

import './index.scss';
import Header from '../../layouts/Header';
import PropTypes from 'prop-types';
import { login, guide, state } from '../../reducers/base';

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
    this.state = {
      consoleUiEnable: true,
      guideMsg: '',
      authSystemType: '',
    };
    this.field = new Field(this);
  }

  componentDidMount() {
    const oidcError = sessionStorage.getItem('oidcError');
    if (oidcError) {
      const { locale = {} } = this.props;
      Message.error(`${locale.oidcAuthFailed || 'OIDC Authentication failed'}: ${oidcError}`);
      sessionStorage.removeItem('oidcError');
    }

    if (localStorage.getItem('token')) {
      const [baseUrl] = location.href.split('#');
      location.href = `${baseUrl}#/`;
    }
    this.handleSearch();
  }

  performOidcRedirect = () => {
    const { locale = {} } = this.props;
    try {
      const contextPath = window.location.pathname.replace(/\/(legacy)(\/.*)?$/, '/') || '/';
      const loginUrl = `${contextPath}v1/auth/oidc/login`;
      console.log('[OIDC] Redirecting to:', loginUrl);
      window.location.href = loginUrl;
    } catch (error) {
      console.error('[OIDC] Failed to redirect to OIDC login:', error);
      Message.error(locale.oidcRedirectFailed || 'Failed to initiate OIDC login. Please try again.');
    }
  };

  handleSearch = () => {
    const { locale = {} } = this.props;
    state()
      .then(res => {
        if (res?.auth_system_type === 'oidc') {
          this.setState({ authSystemType: 'oidc', consoleUiEnable: false });
          return;
        }
        if (res?.console_ui_enabled === 'false') {
          this.setState({ consoleUiEnable: true });
          guide().then(res => {
            this.setState({ guideMsg: res?.data });
          });
        } else {
          this.setState({ consoleUiEnable: false });
        }
      })
      .catch(error => {
        console.error('[OIDC] Failed to fetch authentication state:', error);
        Message.error(locale.getStateFailed || 'Failed to determine authentication method. Please refresh the page.');
      });
  };

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
    const { consoleUiEnable, guideMsg, authSystemType } = this.state;
    const isOidc = authSystemType === 'oidc';

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
            <p className="product-desc">{locale.productDesc}</p>
          </div>
          <div className="animation animation1" />
          <div className="animation animation2" />
          <div className="animation animation3" />
          <div className="animation animation4" />
          <div className="animation animation5" />
          <Card
            className={`login-panel${isOidc ? ' login-panel-oidc' : ''}`}
            contentHeight="auto"
          >
            <div className="login-header">{locale.login}</div>
            {!isOidc && (
              <div className="internal-sys-tip">
                <div>{locale.internalSysTip1}</div>
                <div>{locale.internalSysTip2}</div>
              </div>
            )}
            {isOidc && (
              <div className="oidc-login-block">
                <div className="oidc-login-tip">
                  {locale.ssoLoginTip || 'Single sign-on is enabled. Click the button below to continue.'}
                </div>
                <Button
                  type="primary"
                  size="large"
                  onClick={this.performOidcRedirect}
                >
                  {locale.signInWithSSO || 'Sign in with SSO'}
                </Button>
              </div>
            )}
            {!isOidc && !consoleUiEnable && (
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
                <FormItem label=" ">
                  <Form.Submit onClick={this.handleSubmit}>{locale.submit}</Form.Submit>
                </FormItem>
              </Form>
            )}
            {!isOidc && consoleUiEnable && (
              <Message type="notice" style={{ marginTop: 30 }}>
                <div dangerouslySetInnerHTML={{ __html: guideMsg }} />
              </Message>
            )}
          </Card>
        </section>
      </div>
    );
  }
}

export default Login;
