function Console() {

	this.log = function(object) {
		logger.debug("{}", object);
	}

	this.trace = this.log;

	this.debug = this.log;

	this.info = this.log;

	this.error = this.log;

}