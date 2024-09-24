import { NAME_SHOW } from '../../constants';

const changeNameShow = show => dispatch => {
  localStorage.setItem(NAME_SHOW, show);
};

export default changeNameShow;
