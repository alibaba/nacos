import React from 'react';

import './index.scss';

class Login extends React.Component {
  render() {
    return (
      <div className="home-page">
        <section
          className="top-section"
          style={{
            background: 'url(/img/black_dot.png) repeat',
            backgroundSize: '14px 14px',
          }}
        >
          <div className="vertical-middle">
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
        </section>
      </div>
    );
  }
}

export default Login;
