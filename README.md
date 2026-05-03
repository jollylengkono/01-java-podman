# 01-java-podman

Spring Boot 3 REST API (`GET /users`) backed by PostgreSQL. Both services run as rootless Podman containers on a shared user-defined network; Postgres data is persisted in a named volume. No JDK required on the host — all compilation happens inside a throwaway builder container.

## What was built

```
Containerfile (multi-stage)
  Stage 1 — maven:3.9-eclipse-temurin-21   compile + package  (537 MB, discarded)
  Stage 2 — distroless/java21-debian12     runtime only       (240 MB, shipped)

Containers
  pg   — postgres:16-alpine, data in named volume pg-data
  app  — java-podman-app:dev, port 8080, talks to pg over app-net
```

`GET /users` → Spring Boot → Hibernate → Postgres → JSON array of 3 users.

## Quick start

```bash
podman network create app-net
podman volume create pg-data

podman run -d --name pg --network app-net \
  -e POSTGRES_DB=appdb -e POSTGRES_USER=app -e POSTGRES_PASSWORD=secret \
  -v pg-data:/var/lib/postgresql/data \
  docker.io/library/postgres:16-alpine

podman build -t java-podman-app:dev .

podman run -d --name app --network app-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://pg:5432/appdb \
  -e SPRING_DATASOURCE_USERNAME=app \
  -e SPRING_DATASOURCE_PASSWORD=secret \
  -p 8080:8080 \
  java-podman-app:dev

curl http://localhost:8080/users
```

## Teardown

```bash
podman rm -f app pg
podman network rm app-net
# podman volume rm pg-data   # only if you want to wipe data too
```

## What was learned

### Multi-stage builds
The builder stage uses a full JDK + Maven image (~537 MB) only to produce a JAR. The runtime stage copies just that JAR into a ~194 MB distroless base. Everything else — Maven, the JDK, all downloaded `.m2` dependencies — is discarded. The final image is 240 MB.

`pom.xml` is copied before `src/` deliberately: if only source files change, the `mvn dependency:go-offline` layer is reused from cache. A second build with no `pom.xml` change takes seconds instead of minutes.

### Container networking
A user-defined network (`app-net`) gives each container DNS resolution by container name. The app reaches Postgres at `pg:5432` — `pg` is the `--name` given to the container, not a configured hostname. The default Podman bridge network has no DNS; a named network is always the right choice.

### Volume persistence
`pg-data` lives at `~/.local/share/containers/storage/volumes/pg-data/` on the host. It survives `podman rm -f pg`. A brand-new Postgres container pointed at the same volume picks up the existing cluster with no data loss.

The volume directory shows `Permission denied` from the host shell — Postgres owns those files inside the rootless user namespace. That's expected, not a bug.

### `ddl-auto=create` behaviour
Hibernate drops and recreates the `users` table on every app startup. The Postgres cluster survives container recreation, but the table is always re-seeded from `data.sql`. Data survives at the cluster level, not the table level. Next step for real persistence: switch to `validate` + Flyway migrations.

### Distroless
Swapping `eclipse-temurin:21-jre-alpine` for `gcr.io/distroless/java21-debian12` in one `FROM` line removes the shell entirely. `podman exec app sh` returns exit 127 — there is no `sh`, no `ls`, no `curl` in the image. The app runs fine; there is simply nothing for an attacker to interact with. The distroless image shows a "56 years ago" creation date — Google uses epoch 0 as a reproducible build timestamp.

### Gotchas
- **Short image names don't resolve** on this WSL2/rootless Podman setup. Use fully qualified names: `docker.io/library/postgres:16-alpine`, not `postgres:16-alpine`.
- **`podman restart` does not pick up a rebuilt image.** It reuses the container's original image layer. To run a newly built image, `podman rm -f app` and `podman run` again.
- **`defer-datasource-initialization=true` is required** in Spring Boot 2.5+ when using `data.sql` with JPA. Without it, Spring tries to run `data.sql` before Hibernate has created the table and the seed inserts fail.
