#!/usr/bin/env bash
# Start a new Spring Boot service product from this repo's full layered skeleton.
#
#   tools/new-app.sh billing ~/Documents/projects/billing                 # base package com.billing
#   tools/new-app.sh billing ~/Documents/projects/billing com.acme.billing
#
# Copies the committed tree (git archive HEAD), so the product keeps AGENTS.md, CLAUDE.md,
# tools/ (scaffolding + hook + arch-check), the Jenkinsfile and the arch-qube gate. Copying a
# few packages into a fresh Spring Initializr project loses all of that (AGENTS.md §1).
#
# No --no-demo flag on purpose: User/Auth/OAuthToken are the security foundation every other
# feature authenticates through, the scheduler and plugins/arcana-audit-plugin are the only
# worked examples of Quartz and the OSGi plugin system, and the CI integration stages smoke-test
# the User endpoints. None of it is demo-only. Remove what you do not need afterwards, layer
# by layer, keeping arch-check green.
set -euo pipefail

[ $# -ge 2 ] && [ $# -le 3 ] || { echo "usage: $0 <app-name> <dest-dir> [base-package]" >&2; exit 1; }
APP="$1"
DEST="$2"
[[ "$APP" =~ ^[a-z][a-z0-9]*(-[a-z0-9]+)*$ ]] || { echo "app-name: lower-case letters/digits, words joined by '-' (it becomes the Gradle project, image, Sonar key and CI names)" >&2; exit 1; }
[[ "$APP" =~ ^(arcana|springboot-app)$ ]] && { echo "app-name must differ from the template's names" >&2; exit 1; }
BASE="${3:-com.$(printf '%s' "$APP" | tr -d '-')}"
[[ "$BASE" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]] || { echo "base-package must look like com.acme.billing" >&2; exit 1; }
[ "$BASE" != com.arcana.cloud ] || { echo "base-package must differ from the template's com.arcana.cloud" >&2; exit 1; }
[ ! -e "$DEST" ] || { echo "$DEST already exists — refusing to overwrite" >&2; exit 1; }
command -v perl >/dev/null || { echo "perl is required" >&2; exit 1; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
git -C "$ROOT" diff --quiet HEAD -- AGENTS.md tools/ || echo "note: uncommitted changes in AGENTS.md/tools are NOT copied (git archive HEAD)" >&2

mkdir -p "$DEST"
git -C "$ROOT" archive HEAD | tar -x -C "$DEST"
SRC_SHA="$(git -C "$ROOT" rev-parse --short HEAD)"
BASE_PATH="$(printf '%s' "$BASE" | tr '.' '/')"
cd "$DEST"

# ---- Product identity. Every place the template name is load-bearing:
#  1. Java base package com.arcana.cloud of the application module (src/): directories,
#     package/import statements, MyBatis XML namespaces, @MapperScan/@EnableJpaRepositories
#     strings, type-aliases-package, logging levels, Sonar paths, Gradle group.
#  2. Gradle rootProject.name / spring.application.name / jar + image name arcana-cloud-java.
#  3. CI identities: Jenkins APP_NAME + image "springboot-app", test container springboot-app-test-*,
#     arch-qube container arcana-arch-qube-springboot-*, metrics/Sonar key arcana-cloud-springboot,
#     and every compose project / network / container / kind cluster named arcana-ci-* — two
#     products on one Jenkins host must not delete each other's containers.
#  4. Deployment names arcana-* in compose files and Kubernetes manifests (services, namespace).
# Not renamed on purpose: the framework modules arcana-plugin-api / arcana-plugin-runtime /
# arcana-ssr-engine / arcana-web / plugins/arcana-audit-plugin and their packages com.arcana.cloud.plugin / .ssr /
# com.arcana.plugin (a library contract, like any dependency), the gRPC proto package
# arcana.cloud (wire contract), database names/credentials (per-environment config), the
# Jenkins lock names ci-springboot-build / ci-kind-global (they protect the shared host's RAM,
# so products MUST share them), and arcana.boo (the registry / arch-qube image host).
# Skipped files: tools/ and the agent docs (they describe the template and must keep working),
# docs/ (generated reports and history), CHANGELOG.md (reset below).
FILES="$(grep -rIl --exclude-dir=.git --exclude-dir=tools --exclude-dir=docs \
    --exclude=CHANGELOG.md --exclude=AGENTS.md --exclude=CLAUDE.md --exclude=GEMINI.md \
    --exclude=copilot-instructions.md \
    -e arcana -e springboot-app -e 'Arcana Cloud Spring Boot' . || true)"
[ -n "$FILES" ] || { echo "new-app: nothing to rename — is this the template?" >&2; exit 1; }
printf '%s\n' "$FILES" | APP="$APP" BASE="$BASE" BASE_PATH="$BASE_PATH" xargs perl -pi -e '
    BEGIN { $a = $ENV{APP}; $b = $ENV{BASE}; $bp = $ENV{BASE_PATH} }
    s/com\.arcana\.cloud(?!\.(?:plugin|ssr)\b)(?![\w])/$b/g;
    s{com/arcana/cloud(?!/(?:plugin|ssr)\b)(?![\w])}{$bp}g;
    s/arcana-cloud-(?:java|springboot)(?![A-Za-z0-9_])/$a/g;
    s/arcana-arch-qube-springboot-/$a-arch-qube-/g;
    s/\bspringboot-app-test-/$a-test-/g;
    s/\bspringboot-app\b/$a/g;
    s/Arcana Cloud Spring Boot/$a/g;
    s/\.arcana-cloud\.svc\b/.$a-cloud.svc/g;   # k8s namespace in service DNS names
    s/(?<![\w.-])arcana-(?!plugin|audit-plugin|ssr-engine|web\b|skills|arch-qube|cloud-angular|springboot-developer-skill)([a-z])/$a-$1/g;
'
# group of the application module only (framework modules keep com.arcana.cloud).
grep -q "^group = \"$BASE\"" build.gradle.kts || { echo "new-app: Gradle group not renamed in build.gradle.kts" >&2; exit 1; }
for d in src/main/java src/test/java; do
    [ -d "$d/com/arcana/cloud" ] || { echo "new-app: $d/com/arcana/cloud not found — template layout changed" >&2; exit 1; }
    tmp="$d/.new-app-pkg"
    mv "$d/com/arcana/cloud" "$tmp"
    find "$d/com" -type d -empty -delete 2>/dev/null || true
    mkdir -p "$(dirname "$d/$BASE_PATH")"
    mv "$tmp" "$d/$BASE_PATH"
done
[ -f "$(ls src/main/java/"$BASE_PATH"/*Application.java 2>/dev/null | head -1)" ] \
    || { echo "new-app: @SpringBootApplication class not found under src/main/java/$BASE_PATH" >&2; exit 1; }
# Fail loudly if any application-module package reference survived.
LEFT="$(grep -rIn --exclude-dir=.git --exclude-dir=tools --exclude-dir=docs --exclude=CHANGELOG.md \
    -E 'com[./]arcana[./]cloud([./](config|controller|service|repository|dao|entity|dto|mapper|security|exception|scheduler|document|grpc)\b|;)' \
    src build.gradle.kts sonar-project.properties 2>/dev/null || true)"
if [ -n "$LEFT" ]; then
    echo "new-app: template package names still present after rename:" >&2
    printf '%s\n' "$LEFT" | head >&2
    exit 1
fi

# ---- Fresh history: version 0.1.0, empty changelog.
rm -f CHANGELOG.md
printf '{\n  ".": "0.1.0"\n}\n' > .release-please-manifest.json
perl -pi -e 's/^version = "1\.0\.0-SNAPSHOT"/version = "0.1.0-SNAPSHOT"/' build.gradle.kts
grep -rIl --exclude-dir=.git --exclude-dir=tools --exclude-dir=docs -e '1\.0\.0-SNAPSHOT\.jar' . \
    | xargs perl -pi -e 's/-1\.0\.0-SNAPSHOT\.jar/-0.1.0-SNAPSHOT.jar/g' 2>/dev/null || true
perl -pi -e 's/^(\s*VERSION\s*=\s*)"1\.0\.0"/$1"0.1.0"/' Jenkinsfile
perl -pi -e 's/\$\{VERSION:-1\.0\.0\}/\${VERSION:-0.1.0}/g' docker-compose.ci.yml

cat >> AGENTS.md <<EOT

## Origin

Created from arcana-cloud-springboot @ \`${SRC_SHA}\` by \`tools/new-app.sh\`: base package
\`com.arcana.cloud\` → \`${BASE}\` (so \`<base>\` above is \`src/main/java/${BASE_PATH}\`), project,
image, Sonar and CI names → \`${APP}\`. The layers, rules and definition of done above apply
unchanged. Remove template features you do not need layer by layer, not by flattening.
EOT

git init -q
tools/install-hooks.sh >/dev/null
git add -A
# --no-verify only for this import commit: every file counts as "added" here, so the hook
# would re-judge the whole template at once (and the template is the reference the hook is
# written against). The template itself passes arch-check; every later commit in the product
# goes through the hook.
git -c user.name="${GIT_AUTHOR_NAME:-new-app}" -c user.email="${GIT_AUTHOR_EMAIL:-new-app@localhost}" \
    commit -q --no-verify -m "chore: start ${APP} from arcana-cloud-springboot ${SRC_SHA}"

echo "created $DEST (${APP}, package ${BASE}) from arcana-cloud-springboot ${SRC_SHA}; hooks: $(git config core.hooksPath)"
echo "next: cd $DEST && tools/new-feature.sh <Name>   # one vertical slice per feature"
