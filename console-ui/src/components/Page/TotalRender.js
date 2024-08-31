import React from 'react';

const TotalRender = ({ locale, total }) => {
  return (
    <span style={{ display: `inline-block`, marginLeft: 16, fontSize: 14, color: '#999999' }}>
      {locale.total}
      {total}
    </span>
  );
};

export default TotalRender;
