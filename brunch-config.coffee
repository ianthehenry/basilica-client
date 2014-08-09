exports.config =
  conventions:
    assets: /static/
  paths:
    public: 'out/dev'
    watched: ['styles', 'static']
  files:
    javascripts:
      joinTo:
        'vendor.js': /^bower_components/
    stylesheets:
      joinTo: 'main.css'
  overrides:
    production:
      assets: /static-prod/
      plugins: autoreload: enabled: false
      optimize: true
      paths:
        public: 'out/prod'
        watched: ['styles', 'static-prod']
