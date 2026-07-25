// The published 0.7.9 package is ESM but contains internal relative imports without `.js`
// suffixes. Webpack otherwise treats those imports as fully specified and refuses to bundle
// them. Scope this compatibility rule to DocMentis rather than weakening module resolution for
// the Kotlin/Wasm launcher or any other npm dependency.
config.module.rules.push({
  test: /\.m?js$/,
  include: /node_modules[\\/]@docmentis[\\/]udoc-viewer/,
  resolve: {
    fullySpecified: false,
  },
});
