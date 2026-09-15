// The real Feed Compose interaction resolves successfully after ~3.7s in the
// measured browser run, exceeding Mocha's 2s default. Keep a bounded budget for this module.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 10000 });