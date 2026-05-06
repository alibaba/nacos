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

/**
 * Markdown renderer component
 * Uses marked library from CDN to convert markdown to HTML
 */
class MarkdownRenderer extends React.Component {
  static propTypes = {
    content: PropTypes.string,
    className: PropTypes.string,
  };

  constructor(props) {
    super(props);
    this.state = {
      marked: null,
    };
  }

  componentDidMount() {
    // Load marked library from CDN if not available
    if (window.marked) {
      this.setState({ marked: window.marked });
    } else {
      const script = document.createElement('script');
      script.src = 'https://cdn.jsdelivr.net/npm/marked@4.3.0/marked.min.js';
      script.onload = () => {
        this.setState({ marked: window.marked });
      };
      document.head.appendChild(script);
    }
  }

  render() {
    const { content = '', className = '' } = this.props;
    const { marked } = this.state;

    if (!marked) {
      // Fallback to plain text while loading
      return (
        <div className={`markdown-body ${className}`}>
          <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>
            {content}
          </pre>
        </div>
      );
    }

    try {
      const html = marked.parse(content, {
        breaks: true,
        gfm: true,
      });

      return (
        <div
          className={`markdown-body ${className}`}
          dangerouslySetInnerHTML={{ __html: html }}
        />
      );
    } catch (error) {
      // Fallback to plain text on error
      return (
        <div className={`markdown-body ${className}`}>
          <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>
            {content}
          </pre>
        </div>
      );
    }
  }
}

export default MarkdownRenderer;
