import React from 'react';
import { Card, Form, Input } from '@alifd/next';

import './index.scss';
import Header from '../../layouts/Header';

const FormItem = Form.Item;

class Login extends React.Component {
  handleSubmit = values => {
    console.log('Get form value:', values);
  };

  render() {
    return (
      <div className="home-page">
        <Header />
        <section
          className="top-section"
          style={{
            background: 'url(/img/black_dot.png) repeat',
            backgroundSize: '14px 14px',
          }}
        >
          <div className="vertical-middle product-area">
            <img className="product-logo" src="/img/nacos.png" />
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
            <div className="login-header">请登录</div>
            <Form className="login-form">
              <FormItem>
                <Input htmlType="text" name="username" placeholder="请输入账号名" />
              </FormItem>
              <FormItem>
                <Input htmlType="password" name="password" placeholder="请输入密码" />
              </FormItem>
              <FormItem label=" ">
                <Form.Submit onClick={this.handleSubmit}>提交</Form.Submit>
              </FormItem>
            </Form>
          </Card>
        </section>
      </div>
    );
  }
}

export default Login;
