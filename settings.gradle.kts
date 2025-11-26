rootProject.name = "arcana-cloud-java"

// Plugin Framework Modules
include(":arcana-plugin-api")
include(":arcana-plugin-runtime")
include(":arcana-ssr-engine")

// Sample Plugins
include(":plugins:arcana-audit-plugin")

// Web Applications (Node.js based - separate build)
// include(":arcana-web:react-app")
// include(":arcana-web:angular-app")
