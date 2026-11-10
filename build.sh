#!/bin/sh
# Builds source/ into build/, so that start.sh only starts programs and needs no install or build.
# Needs Java 25 and Node 22 or later. Maven comes from source/backend/mvnw.
#   build/backend/     the Spring Boot backend, extracted: the application jar and lib/ with its
#                      packages. GitHub refuses files over 100 MB, and the single jar is larger.
#   build/browser/     the frontend with its source maps, for atg (browser_dir)
#   build/www/         the same frontend without the maps, which nginx serves
set -eu
cd "$(dirname "$0")"
rm -rf build
mkdir -p build

(cd source/backend && ./mvnw -B -q -Pdev -Dmaven.test.skip=true package)
java -Djarmode=tools -jar source/backend/target/quizzes-tutor-backend-0.0.1-SNAPSHOT.jar extract --destination build/backend

# The frontend calls the API at /api on its own origin, and nginx sends /api/ to the backend.
# No Fenix login in atg: its link goes to a closed local port.
# CYPRESS_INSTALL_BINARY=0: package.json still lists Cypress, but the build does not need its binary.
(cd source/frontend && CYPRESS_INSTALL_BINARY=0 npm ci --no-audit --no-fund && \
  VUE_APP_NAME="Quizzes Tutor" VUE_APP_ROOT_API=/api VUE_APP_FENIX_URL=http://127.0.0.1:9/ \
  npx vite build --outDir ../../build/browser --emptyOutDir)

cp -R build/browser build/www
find build/www -name '*.map' -delete

# Check the limit of GitHub.
big=$(find build -type f -size +95M)
[ -z "$big" ] || { echo "files over 95 MB: $big" >&2; exit 1; }
