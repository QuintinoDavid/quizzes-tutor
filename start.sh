#!/bin/sh
# Starts Quizzes Tutor for atg on http://localhost:5181, from the files that build.sh made.
# Postgres, the backend and nginx each run in a container on a private Docker network, as in
# the upstream compose file. Only nginx is published, on 127.0.0.1:5181, and the backend on
# 127.0.0.1:8091 for the readiness check. This works with Docker on Linux and with Docker
# Desktop on macOS, because nothing uses the host network.
# Order: Postgres, then the backend, then nginx. nginx opens the port only when the backend
# answers, and the backend answers only when the seed is in the database, so atg never sees
# a half-started app.
# Needs Docker, curl and Node (atg needs Node too), and the images postgres:18-alpine,
# eclipse-temurin:25-jre and nginx:1.27-alpine. Pull them first: a pull during the start
# counts against the start limit of atg.
set -eu
cd "$(dirname "$0")"

NET=atg-quizzes-tutor
DB=atg-quizzes-tutor-postgres
BACKEND=atg-quizzes-tutor-backend
WEB=atg-quizzes-tutor-nginx
BACKEND_PORT=8091
# Nothing listens there, so a call to a service that atg does not start fails at once.
NOWHERE=http://127.0.0.1:9

cleanup() {
  docker rm -f "$DB" "$BACKEND" "$WEB" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}

# A run that crashed can leave the containers behind. An old backend would also answer the
# readiness check below in place of the new one.
cleanup

# atg stops the app with SIGKILL, so a trap never runs, and Docker keeps the containers.
# A watcher removes them when this script is gone. It must not be in the process tree or
# the process group of this script, or atg kills it too: Node starts it in a new session
# (detached), which works on Linux and macOS, where setsid is not available.
parent=$$
node -e 'require("child_process").spawn("sh", ["-c", process.argv[1]], { detached: true, stdio: "ignore" }).unref()' \
  "while kill -0 $parent 2>/dev/null; do sleep 0.5; done
   docker rm -f $DB $BACKEND $WEB >/dev/null 2>&1; docker network rm $NET >/dev/null 2>&1"
# Ctrl-C, when a person runs the script by hand.
trap 'cleanup; exit 130' INT TERM

docker network create "$NET" >/dev/null

# A new, empty database in memory (tmpfs) at each start. It has no published port: only the
# containers on the network reach it, as "postgres".
docker run -d --rm --name "$DB" --network "$NET" --network-alias postgres \
  -e POSTGRES_USER=tutor -e POSTGRES_PASSWORD=tutor -e POSTGRES_DB=tutordb \
  --tmpfs /var/lib/postgresql \
  postgres:18-alpine >/dev/null
until docker exec "$DB" pg_isready -U tutor -d tutordb -h 127.0.0.1 >/dev/null 2>&1; do sleep 0.5; done

# Profile dev, as in development. The demo course gets its content from the seed only
# (--atg.seed=true with --demo.reset-on-startup=false), so every start has the same data.
# --scheduled-tasks.enabled=false: no nightly reset of the demo course, export or statistics.
# The backend needs values for Fenix (the login of Tecnico), the fraud service and the email,
# even unused: fake-host makes the app log the emails and not send them.
# Uploaded images stay inside the container, so they go away with it.
docker run -d --rm --name "$BACKEND" --network "$NET" --network-alias backend \
  -p 127.0.0.1:$BACKEND_PORT:8080 \
  -v "$PWD/build/backend:/app:ro" \
  eclipse-temurin:25-jre \
  java -jar /app/quizzes-tutor-backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  --server.port=8080 \
  --spring.datasource.url=jdbc:postgresql://postgres:5432/tutordb \
  --spring.datasource.username=tutor --spring.datasource.password=tutor \
  --spring.datasource.driver-class-name=org.postgresql.Driver \
  --spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
  --spring.mail.host=fake-host --spring.mail.port=25 --spring.mail.username=atg@localhost --spring.mail.password=none \
  --oauth.consumer.key=atg --oauth.consumer.secret=atg \
  --base.url=$NOWHERE --callback.url=http://localhost:5181/login \
  --fraud.service.url=$NOWHERE/ --aqg.service.url=$NOWHERE \
  --figures.dir=/tmp/figures/ --export.dir=/tmp/export/ --load.dir=/tmp/load/ \
  --demo.reset-on-startup=false --scheduled-tasks.enabled=false --atg.seed=true >/dev/null
# The backend log goes to the output of this script, which atg keeps in its app log.
docker logs -f "$BACKEND" &

# Any HTTP answer means that the backend is up. Stop if the backend exits first.
until curl -s -o /dev/null http://127.0.0.1:$BACKEND_PORT/; do
  [ -n "$(docker ps -q -f name=^$BACKEND\$)" ] || { echo "the backend exited before it answered" >&2; cleanup; exit 1; }
  sleep 0.5
done

# nginx serves build/www and sends /api/ to the backend. It runs in the background and the
# script waits for it: sh runs a trap only after a foreground command ends, but a signal
# interrupts wait, so a SIGTERM to this script stops the app at once.
docker run --rm --name "$WEB" --network "$NET" \
  -p 127.0.0.1:5181:5181 \
  -v "$PWD/nginx.conf:/etc/nginx/nginx.conf:ro" \
  -v "$PWD/build/www:/app:ro" \
  nginx:1.27-alpine &
wait $!
