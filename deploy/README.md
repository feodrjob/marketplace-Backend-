# Деплой: GitHub Actions → GHCR → Docker Swarm

Целевая VM: `87.242.102.190`. Адрес приложения: `https://87.242.102.190.nip.io`.
nip.io возвращает IP из имени; сертификат выпускает и обновляет Traefik через
Let's Encrypt HTTP-01. Для проверки домена извне должны быть доступны TCP 80/443.
SSH остаётся на TCP 22. База данных и порты приложений наружу не публикуются.

## Стеки

| Стек | Сервисы | Когда обновляется |
| --- | --- | --- |
| `marketplace-edge` | Traefik | Отдельное изменение инфраструктуры |
| `marketplace-data` | PostgreSQL 17 | Отдельное изменение инфраструктуры |
| `marketplace-backend` | auth, catalog | Push backend в main |
| `marketplace-frontend` | frontend/nginx | Push frontend в main |

Traefik направляет `/api/v1/auth` и `/api/v1/users` в auth:8081, пути
`/api/v1/products` и `/api/v1/categories` зарезервированы для catalog:8083,
остальные запросы — во frontend:80. URL API фронтенда остаётся `/api/v1`.
Контракт HTTP API каталога нужно подтвердить после его реализации.

**Catalog временно имеет 0 реплик.** Образ собирается вместе с auth, но runtime
не участвует в успешности деплоя. После исправления установить repository
variable `CATALOG_REPLICAS=1` и запустить backend pipeline повторно.
Само наличие маршрута не реализует отсутствующие HTTP-контроллеры каталога.

## Pipeline

`build.yml`: проверки из reusable `ci.yml` → сборка auth/catalog → публикация
`ghcr.io/feodrjob/marketplace-{auth,catalog}:<commit SHA>` → reusable `deploy.yml`.
Деплой выполняется только для main. Ветка `codex/swarm-deploy` проходит проверки
и публикует образы для подготовки миграции, но приложение не переключает.
Проверки PR не запускают деплой.

SSH передаёт только release.sh и manifest. VM больше не клонирует код и не
запускает Gradle. Токен Actions передаётся по SSH через stdin, docker login
использует временный DOCKER_CONFIG, удаляемый после деплоя. `--with-registry-auth`
передаёт разрешение Swarm. Токен имеет ограниченный срок жизни: на текущем
одиночном узле образы остаются в локальном кеше. Перед добавлением узлов или
автоматической очисткой образов нужен отдельный read:packages token с подходящим
сроком жизни. Не запускайте `docker image prune -a` без учёта этого ограничения.

У каждого репозитория собственный стек и история версий. Общий `flock` на VM
не допускает одновременных изменений стеков. Устаревший commit проверяется перед
отправкой. Обновления сервисов используют start-first и rollback при ошибке;
release.sh дополнительно ожидает готовность и проверяет HTTPS, затем сохраняет
успешный manifest. При неуспешном деплое предыдущий manifest применяется снова,
а Actions завершается ошибкой. Схема БД автоматически назад не откатывается.

## GitHub settings

В обоих репозиториях используются существующие Actions secrets:
`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`.
Host key должен быть проверен заранее; StrictHostKeyChecking не отключается.

Repository variables:

- `DEPLOY_PORT`: по умолчанию `22`.
- `APP_DOMAIN`: по умолчанию `87.242.102.190.nip.io`.
- `CATALOG_REPLICAS`: для backend по умолчанию `0`, разрешены `0` или `1`.

GITHUB_TOKEN должен иметь packages:write на сборке и packages:read на деплое.
Пакет GHCR должен разрешать доступ своему репозиторию в Manage Actions access.
Публиковать приватные образы публично для этого не требуется.

## Однократная миграция существующей VM

Работает на текущей VM с Compose-контейнерами `marketplace-backend-*-1`.
Перед переключением убедиться, что образы auth/catalog/frontend опубликованы,
скачиваются с VM и проверены; старые deploy workflows должны быть заменены или
временно выключены, чтобы Compose не запустился параллельно со Swarm.

1. Передать каталог `deploy/swarm` в `~/.marketplace-swarm/infra` на VM.
2. Выполнить `bash ~/.marketplace-swarm/infra/prepare.sh`. Скрипт сначала проверит
   GHCR, ACME и DNS, скачает инфраструктурные образы, сделает pg_dump и проверит
   восстановление в отдельном PostgreSQL с `--network none` и временными данными.
   Затем включит Swarm, создаст сети/секреты и закрепит data label за VM.
   Существующие Compose-контейнеры и nginx на этом шаге продолжают работать.
3. Пароль PostgreSQL переносится из текущего контейнера в Swarm secret без
   вывода. JWT secret генерируется заново: пользователям потребуется войти снова.
4. В согласованное окно выполнить `bash ~/.marketplace-swarm/infra/cutover.sh`.
   Он остановит старые auth/catalog/db, освободит 80/443 от host nginx и запустит
   стеки data/edge. Том `marketplace-backend_postgres-data` сохраняется. Новая и
   старая PostgreSQL никогда не должны работать с ним одновременно.
5. Развернуть backend, затем frontend из успешных сборок. Новые push в main
   делают это автоматически. Первому выпуску сертификата может потребоваться
   несколько минут. `ACME_EMAIL` можно задать при применении edge.yml; по
   умолчанию регистрация ACME выполняется без email.
6. Проверить: HTTP перенаправляет на HTTPS; `/` даёт 200; `/api/v1/users/me` без
   токена даёт 401; вход/refresh работают по HTTPS; сертификат доверенный.
7. Старые остановленные Compose-контейнеры сохраняются для отката. После проверки
   убрать прежний frontend с опубликованным 8082. Том БД и volume сертификатов
   не удалять. Для резервных копий нужен отдельный регулярный процесс.

Архив БД и checksum находятся в `~/.marketplace-swarm/backups` с доступом только
владельцу. Marker `ready` разрешает CI-деплой только после успешного cutover.

## Диагностика и откат

```bash
docker stack ls
docker stack services marketplace-backend
docker service ps --no-trunc marketplace-backend_auth
docker service logs --tail 100 marketplace-edge_traefik
docker service logs --tail 100 marketplace-backend_auth
curl -I https://87.242.102.190.nip.io
```

История спецификаций: `~/.marketplace-swarm/<stack>.{current,previous}.yml`.
Для отката приложения применить предыдущий manifest через `docker stack deploy`
и дождаться обновления сервиса. Сохранять старые образы до конца окна отката.

Если первый переход не удался, остановить новые backend/frontend, удалить
стек data и **дождаться полной остановки новой PostgreSQL**, затем запустить
старые Compose-контейнеры и host nginx. Не восстанавливать dump поверх работающей
БД. cutover.sh автоматически возвращает старую БД/auth/nginx при ошибке на своём
этапе; ошибки последующих релизов требуют отдельной проверки.

Эта конфигурация рассчитана на одну VPS: падение VM остановит весь сервис.
Перед добавлением узлов нужны отдельный план доступа к данным, постоянные
registry credentials и настройка приватной связи Swarm. Менеджер сейчас слушает
только loopback:2377. Docker socket доступен только контейнеру Traefik, dashboard
не опубликован. Монтирование socket read-only не является полноценной изоляцией
Docker API; доступ к этому контейнеру следует считать привилегированным.

`deploy/install.sh` и `deploy/package.py` — прежняя схема Cloud.ru registry;
новые workflows их не используют. Локальный compose.yaml остаётся для разработки.
