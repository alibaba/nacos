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
 * Magic Wand Icon Component
 * A custom SVG icon for AI-related actions
 */
const MagicWandIcon = ({ size = 16, style = {}, className = '' }) => {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      style={style}
      className={className}
    >
      {/* Magic wand stick */}
      <path
        d="M4 20L20 4"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* Star at top of wand */}
      <path
        d="M4 4L6 6L4 8L6 6L8 4L6 6L4 4Z"
        fill="currentColor"
      />
      {/* Sparkles around the wand */}
      <circle cx="18" cy="6" r="1.5" fill="currentColor" />
      <circle cx="6" cy="18" r="1.5" fill="currentColor" />
      <circle cx="11" cy="13" r="1" fill="currentColor" />
      <circle cx="13" cy="11" r="1" fill="currentColor" />
    </svg>
  );
};

MagicWandIcon.propTypes = {
  size: PropTypes.number,
  style: PropTypes.object,
  className: PropTypes.string,
};

export default MagicWandIcon;
