// Compose 1.10's Wasm launcher imports `./skiko.mjs`, but this Android/KMP module shape does
// not copy that runtime beside the generated launcher before Webpack resolves its imports.
// Materialize only Compose's extracted JS/WASM pair in the generated launcher directory.
// This is build output, not a public static-file route; application imports and third-party
// module resolution remain unchanged.
const fs = require('fs');
const path = require('path');

// Config fragments are concatenated into build/wasm/packages/Quata-web/webpack.config.js.
const runtimeDirectory = path.resolve(__dirname, '../../../../web/build/compose/skiko-for-web-runtime');
const launcherDirectory = path.resolve(__dirname, 'kotlin');
fs.mkdirSync(launcherDirectory, { recursive: true });
for (const filename of ['skiko.mjs', 'skiko.wasm']) {
  fs.copyFileSync(path.join(runtimeDirectory, filename), path.join(launcherDirectory, filename));
}
