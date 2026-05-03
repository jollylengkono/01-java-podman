# 01-java-podman

Spring Boot 3 REST API (`GET /users`) backed by PostgreSQL. Both services run as rootless Podman containers on a shared user-defined network; Postgres data is persisted in a named volume. No JDK required on the host — all compilation happens inside a throwaway builder container.

## Quick start (after Phase 3)

```bash
podman network create app-net
podman volume create pg-data

podman run -d --name pg --network app-net \
  -e POSTGRES_DB=appdb -e POSTGRES_USER=app -e POSTGRES_PASSWORD=secret \
  -v pg-data:/var/lib/postgresql/data \
  postgres:16-alpine

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
