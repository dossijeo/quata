// Feed Compose interaction tests now include durable video-position restoration before media
// handoff. Keep a bounded budget that covers that asynchronous startup in the browser runner.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 20000 });

// This Compose suite can occupy the browser main thread for longer than
// Karma's defaults on shared runners. Keep both waits bounded, and allow the
// browser to reconnect instead of failing the entire Web/Wasm lane.
config.browserNoActivityTimeout = 120000;
config.browserDisconnectTimeout = 60000;
config.browserDisconnectTolerance = 2;

if (process.env.CI) {
  config.set({
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: "ChromeHeadless",
        flags: ["--no-sandbox", "--disable-dev-shm-usage"],
      },
    },
    browsers: ["ChromeHeadlessNoSandbox"],
  });
}
