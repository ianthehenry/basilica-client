exports.config =
  conventions:
    assets: /^static\/dev/
  paths:
    public: 'out/dev'
    watched: ['styles', 'static/dev']
  files:
    javascripts:
      joinTo:
        'vendor.js': /^bower_components/
    stylesheets:
      joinTo: 'main.css'
  overrides:
    production:
      conventions:
        assets: /^static\/prod/
      plugins: autoreload: enabled: false
      optimize: true
      paths:
        public: 'out/prod'
        watched: ['styles', 'static/prod']
