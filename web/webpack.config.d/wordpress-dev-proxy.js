// Development-only same-origin bridge for the WordPress composer endpoints.  Production
// bundles do not use this route: the runtime adapter emits the configured HTTPS origin.
// Keeping the two paths here avoids weakening WordPress CORS policy just for local Wasm.
config.devServer = config.devServer || {};
config.devServer.proxy = config.devServer.proxy || [];
config.devServer.proxy.push({
  context: ['/wordpress-proxy'],
  target: 'https://egquata.com',
  changeOrigin: true,
  secure: true,
  pathRewrite: { '^/wordpress-proxy': '' },
});
