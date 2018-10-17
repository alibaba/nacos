module.exports = {
	set: function(key,value){
		window.localStorage.setItem(key,value);
	},
	get: function(key) {
		return window.localStorage.getItem(key);
	},
	remove: function(key) {
		window.localStorage.removeItem(key);
	}

}