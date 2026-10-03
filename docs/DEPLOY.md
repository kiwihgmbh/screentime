# Deploying

Written for one family on one small machine: a VPS, a NAS, a Raspberry Pi 4 or
better, or a spare box in a cupboard. There is no cluster and nothing to scale.

Every host name below is `screentime.example.com`. Use your own.

## What you need

- Docker with the Compose plugin
- About 1 GB of memory for the three containers
- A name pointing at the machine, and something in front doing TLS

## First run

```sh
cp .env.example .env
$EDITOR .env
docker compose up --build -d
```

Fill in `.env` before the first start. Generate both secrets properly:

```sh
openssl rand -base64 48    # once for JWT_SECRET
openssl rand -base64 24    # once for POSTGRES_PASSWORD
```

The application refuses to start if `APP_ADMIN_USER`, `APP_ADMIN_PASSWORD` or
`JWT_SECRET` is missing, if the secret is shorter than 32 characters, or if
`APP_TIMEZONE` is not a zone the machine knows. It names the variable and what
to do about it:

```
***************************
APPLICATION FAILED TO START
***************************

Description:

JWT_SECRET is only 9 characters long. HS256 needs at least 32.

Action:

Generate one with: openssl rand -base64 48
```

That is deliberate. This repository is public, and a default secret in a
family's screen time account is a default secret on the internet.

Then:

1. Open `https://screentime.example.com` and sign in as `APP_ADMIN_USER`.
2. Change that password under **Accounts**.
3. Create the child's account under **Accounts**, role `CHILD`.
4. Check the numbers under **Settings**. The defaults were written for an
   eleven year old.

`APP_ADMIN_PASSWORD` is only read when no parent account exists yet. Changing
it in `.env` later does nothing; change the password in the app.

## What runs

| Service | Image | Published | Notes |
| --- | --- | --- | --- |
| `screentime-db` | `postgres:16-alpine` | no | data in the `screentime-db-data` volume |
| `screentime-backend` | built from `./backend` | no | reachable only from the frontend |
| `screentime-frontend` | built from `./frontend` | yes | nginx: the app, and `/api` proxied to the backend |

Only the frontend publishes a port, and by default only on `127.0.0.1`. The
browser talks to one origin, so there is one host name, one certificate and no
CORS to configure.

`docker compose up` waits for each service to be healthy before starting the
next, so the backend never races the database and the frontend never serves
before the API answers.

## TLS and the reverse proxy

Compose binds to `127.0.0.1:8080`. Put a proxy in front of it. With Caddy that
is the whole configuration:

```caddyfile
screentime.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

With nginx on the host:

```nginx
server {
    listen 443 ssl;
    server_name screentime.example.com;

    ssl_certificate     /etc/letsencrypt/live/screentime.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/screentime.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Set `APP_PUBLIC_URL=https://screentime.example.com` in `.env` to match.

Only set `APP_BIND_ADDRESS=0.0.0.0` if you know what is in front of it. Serving
this over plain HTTP sends the child's password and token in clear text across
the network.

### Running it on the home network only

If the app never leaves the house, point a local name at the machine and skip
the certificate. It is still worth knowing that without TLS anyone on the
wifi can read the token.

## Time zones

`APP_TIMEZONE` decides every day and week boundary. Instants are stored in UTC
and the calendar is derived in that zone, so the containers all run with their
clock set to it.

This matters for one thing in particular: the scheduler that closes forgotten
sessions runs at 23:59 **local** time. If the container's zone is wrong, a
session started in the evening gets attributed to the wrong day, and the
weekly comparison stops making sense.

Two days a year are 23 and 25 hours long. Budgets are in minutes of use and do
not change on those days; the tests cover both.

## Backups

The whole account is one small database. A nightly dump is enough.

```sh
docker compose exec -T screentime-db \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists \
  | gzip > "screentime-$(date +%F).sql.gz"
```

Restoring into an empty database:

```sh
gunzip -c screentime-2026-01-01.sql.gz \
  | docker compose exec -T screentime-db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
```

Keep the dumps off the machine as well as on it, and keep `.env` with them:
the dump is useless without the database password, and the tokens issued
before a `JWT_SECRET` change stop working. `*.sql.gz` is in `.gitignore`, so a
dump left in the working directory will not be committed by accident.

## Updating

```sh
git pull
docker compose up --build -d
```

Flyway applies any new migrations on startup. Take a dump first; the project is
early and a migration has not yet had to be undone, but the first time will
happen eventually.

## Automatic deploys

Optional. `.github/workflows/deploy.yml` runs the tests on every push, and on
`main` it builds both images, pushes them to `ghcr.io/<owner>`, logs into the
server over SSH and restarts the stack with them. The server then never builds
anything, which matters on a small machine: the Maven and Angular builds want
more memory than the running app.

On the server, once:

```sh
sudo adduser --disabled-password --gecos "" deploy
sudo usermod -aG docker deploy
sudo mkdir -p /opt/screentime && sudo chown deploy:deploy /opt/screentime
sudo -u deploy git clone https://github.com/<owner>/screentime.git /opt/screentime
sudo -u deploy cp /opt/screentime/.env.example /opt/screentime/.env
sudo -u deploy $EDITOR /opt/screentime/.env     # as in "First run"
sudo chmod 600 /opt/screentime/.env
```

If another app on the machine already uses `127.0.0.1:8080`, set `APP_PORT` in
`.env` to a free port and point the proxy at that one.

Make a key pair for the workflow, put the public half in
`/home/deploy/.ssh/authorized_keys` and keep the private half for GitHub:

```sh
ssh-keygen -t ed25519 -f screentime-deploy -C "github-actions" -N ""
```

In the repository under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `VPS_HOST` | the server's address |
| `VPS_USER` | `deploy` |
| `VPS_SSH_KEY` | the private key, the whole file |
| `GHCR_PULL_TOKEN` | only if the images are private: a token with `read:packages` |

and, if the checkout is not in `/opt/screentime`, the variable
`DEPLOY_REPO_DIR`. Without `VPS_HOST` the deploy job is skipped and the
workflow only tests and publishes the images.

Images built from a public repository are public on ghcr.io. If the first
build creates them as private, either make them public under the package's
settings or set `GHCR_PULL_TOKEN`.

To bring it up by hand, or to roll back to an earlier build, use the same two
files the workflow uses and pick the tag, which is the commit SHA:

```sh
export IMAGE_REGISTRY=ghcr.io/<owner> IMAGE_TAG=<commit-sha or latest>
docker compose -f docker-compose.yml -f deploy/compose.registry.yml pull
docker compose -f docker-compose.yml -f deploy/compose.registry.yml up -d --no-build --wait
```

## Checking on it

```sh
docker compose ps                                  # all three should say healthy
docker compose logs -f screentime-backend          # what the application is doing
curl -fsS http://127.0.0.1:8080/healthz            # the frontend container
docker compose exec screentime-backend \
  curl -fsS http://localhost:8080/actuator/health  # the backend and its database
```

Only `/actuator/health` is exposed, and it answers `UP` or `DOWN` without
saying anything about what is inside.

Every change to a session, an adjustment, a check, a setting or an account is
in `audit_log`, with the old value, the new value, who did it and when. That is
the table to read when a number is not what someone expected:

```sh
docker compose exec screentime-db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "select at, entity, action, old_value, new_value from audit_log order by at desc limit 20"
```

## When something is wrong

**It will not start, and names a variable.** Read the `Action:` line. It is
`.env`.

**It will not start, and mentions the database.** The backend waits for the
database to be healthy, so this is usually a wrong `POSTGRES_PASSWORD` in a
`.env` that was changed after the volume was created. The password in the
volume is the one from the first start; either put it back, or remove the
volume and restore from a dump.

**Signing in works, but the dashboard says there is no child account.** Create
one under **Accounts**. The account the app keeps is the child's.

**A session is stuck running.** The scheduler closes it at 23:59 local time and
caps it at what the day had left. A parent can also stop it, and correct the
minutes afterwards; the entry is flagged as closed automatically so it is
obvious what happened.

**The child was signed out unexpectedly.** Tokens last `JWT_TTL_DAYS`, 30 by
default. Changing `JWT_SECRET` invalidates every token immediately, which is
how you sign everyone out on purpose.

## Development

Not needed to run the app, only to change it.

```sh
cd backend  && ./mvnw verify          # build and test, needs Docker for Testcontainers
cd backend  && ./mvnw spring-boot:run # against a local postgres
cd frontend && npm ci && npm test
cd frontend && npm start              # dev server on :4200, proxying /api to :8080
```

The images do not run the tests. They need a Docker daemon of their own, and a
build that silently skipped them would be worse than one that never claimed to
run them. Run `./mvnw verify` and `npm test` before building.

```sh
cd frontend && npm run extract-i18n   # messages for a translation
```
