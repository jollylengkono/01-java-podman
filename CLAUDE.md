# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Global conventions (container runtime, shell, commit style) live in `../CLAUDE.md`. This file covers only what is specific to `01-java-podman`.

## What this project is

Spring Boot 3 REST API with one endpoint (`GET /users`) backed by PostgreSQL. Both services run as rootless Podman containers on a shared user-defined network, with Postgres data in a named volume.

## Key commands

All container builds and runs use `podman`, never `docker`.

### Build the app image (multi-stage, no local JDK needed)
```bash
podman build -t java-podman-app:dev .
```

### Start Postgres
```bash
podman run -d \
  --name pg \
  --network app-net \
  -e POSTGRES_DB=appdb \
  -e POSTGRES_USER=app \
  -e POSTGRES_PASSWORD=secret \
  -v pg-data:/var/lib/postgresql/data \
  postgres:16-alpine
```

### Start the app
```bash
podman run -d \
  --name app \
  --network app-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://pg:5432/appdb \
  -e SPRING_DATASOURCE_USERNAME=app \
  -e SPRING_DATASOURCE_PASSWORD=secret \
  -p 8080:8080 \
  java-podman-app:dev
```

### Smoke-test the endpoint
```bash
curl http://localhost:8080/users
```

### Teardown
```bash
podman rm -f app pg
podman network rm app-net
# volume is intentionally kept; add `podman volume rm pg-data` to also wipe data
```

### Run Maven tests inside a throwaway container (no local JDK)
```bash
podman run --rm -v "$(pwd)":/work -w /work docker.io/library/maven:3.9-eclipse-temurin-21 mvn test
```

## Architecture

```
Containerfile  (multi-stage)
  Stage 1 — builder: maven:3.9-eclipse-temurin-21
    copies pom.xml + src/, runs `mvn package -DskipTests`
  Stage 2 — runtime: eclipse-temurin:21-jre-alpine  (or gcr.io/distroless/java21)
    copies target/*.jar, sets ENTRYPOINT

src/
  main/java/.../
    UsersController   — @RestController, GET /users
    User              — @Entity mapped to `users` table
    UserRepository    — Spring Data JPA repository
  main/resources/
    application.properties  — datasource URL/credentials (overridden by env vars at runtime)
    schema.sql (or Flyway)  — creates `users` table and seeds rows
```

## Networking model

A user-defined Podman network (`app-net`) lets containers resolve each other by container name as DNS hostnames. The app references Postgres as `pg:5432` — the container name becomes the hostname. This is equivalent to how managed servers in a WebLogic cluster resolve each other by listen-address name.

## Data persistence

`pg-data` is a named Podman volume. It survives `podman rm` of the Postgres container. Deleting it (`podman volume rm pg-data`) is the equivalent of dropping and recreating an Oracle data file — do it intentionally.

## Containerfile notes

- The builder stage uses a full JDK image only to compile; the final image carries only the JRE, keeping it small.
- `eclipse-temurin:21-jre-alpine` is the pragmatic default (~90 MB). Switch the `FROM` in stage 2 to `gcr.io/distroless/java21-debian12` for a no-shell, minimal-attack-surface image — useful once the basics work.
- `.dockerignore` (or `.containerignore`) must exclude `target/` to avoid cache-busting the dependency download layer on every build.
