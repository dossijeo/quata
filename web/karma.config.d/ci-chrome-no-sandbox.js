// GitHub's Ubuntu runners can deny Chromium's user-namespace sandbox.
// Keep the production/browser configuration unchanged; this only replaces the
// Karma launcher when CI is set by the runner.
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
