// freepeepee : Pug as Angular HTML preprocessor
// Resolves *.pug via @webdiscus/pug-loader -> raw HTML, fed to Angular compiler.
module.exports = {
  module: {
    rules: [
      {
        test: /\.pug$/,
        oneOf: [
          // Component templates : `templateUrl: './foo.component.pug'`
          { resourceQuery: /\?ngResource/, use: { loader: '@webdiscus/pug-loader', options: { method: 'render' } } },
          // Inline `template: require('./foo.pug')` (fallback)
          { use: { loader: '@webdiscus/pug-loader', options: { method: 'render' } } }
        ]
      }
    ]
  }
};
