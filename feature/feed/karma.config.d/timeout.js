// Feed Compose interaction tests now include durable video-position restoration before media
// handoff. Keep a bounded budget that covers that asynchronous startup in the browser runner.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 20000 });
