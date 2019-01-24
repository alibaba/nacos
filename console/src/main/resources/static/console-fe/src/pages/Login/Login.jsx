import React from 'react';
import { Card, Form, Input, Message, ConfigProvider, Field } from '@alifd/next';
import { withRouter } from 'react-router-dom';

import './index.scss';
import Header from '../../layouts/Header';
import { request } from '../../globalLib';
import PropTypes from 'prop-types';

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

  handleSubmit = () => {
    const { locale = {} } = this.props;
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }
      request({
        type: 'post',
        url: 'v1/auth/login',
        data: values,
        success: ({ code, data }) => {
          if (code === 200) {
            // TODO: 封装一个方法存储、读取token
            localStorage.setItem('token', data);
            // TODO: 使用react router
            this.props.history.push('/');
          }
          if (code === 401) {
            Message.error({
              content: locale.invalidUsernameOrPassword,
            });
          }
        },
        error: () => {
          Message.error({
            content: locale.invalidUsernameOrPassword,
          });
        },
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
