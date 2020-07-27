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
import { Card, Form, Input, Message, ConfigProvider, Field } from '@alifd/next';
import { withRouter } from 'react-router-dom';

import './index.scss';
import Header from '../../layouts/Header';
import PropTypes from 'prop-types';
import { login } from '../../reducers/base';

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
  }

  componentDidMount() {
    if (localStorage.getItem('token')) {
      const [baseUrl] = location.href.split('#');
      location.href = `${baseUrl}#/`;
    }
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
          </Card>
        </section>
      </div>
    );
  }
}

export default Login;
