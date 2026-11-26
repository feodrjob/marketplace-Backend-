# Swarm-деплой по примеру ментора

Два стека на одной VPS:

- `traefik` — Traefik v2.11, HTTP/HTTPS и сертификаты Let's Encrypt.
- `marketplace` — PostgreSQL, auth, frontend и временно выключенный catalog.

Адрес: `https://87.242.102.190.nip.io`. nip.io предоставляет DNS-имя;
сертификат получает Traefik через HTTP-01 на порту 80.

## Файлы

| Файл | Назначение на VPS |
| --- | --- |
| `traefik-stack.yml` | `/opt/marketplace/infra/traefik-stack.yml` |
| `traefik/traefik.yml` | `/opt/marketplace/infra/traefik/traefik.yml` |
| `app-stack.yml` | `/opt/marketplace/app-stack.yml` |
| `.env.example` | Образец для `/opt/marketplace/.env` |

`app-stack.yml` одинаковый в backend и frontend репозиториях. Оба Deploy
загружают его на VPS в одно и то же место. При изменении состава стека
поддерживать обе копии одинаковыми.

## Автоматический деплой

1. Push в main запускает workflow с названием `Build`.
2. После CI он собирает и публикует образы в GHCR с тегами latest и SHA.
3. `Deploy` из `.github/workflows/deploy-swarm.yml` получает событие workflow_run после успешного Build для main.
4. Checkout читает app-stack.yml; appleboy/ssh-action передаёт его на VPS и запускает деплой в одном SSH-сеансе, без отдельных SCP-подключений.
5. В том же SSH-сеансе пользователь krefature загружает серверный .env и выполняет:

```bash
set -a; source /opt/marketplace/.env; set +a
docker stack deploy \
  -c /opt/marketplace/app-stack.yml \
  --with-registry-auth \
  --resolve-image always \
  --detach=false \
  marketplace
```

Используются environment `production` и существующий Actions secret
`DEPLOY_SSH_KEY`. Ключ должен предоставлять SSH-доступ krefature на VPS;
пользователь должен иметь доступ к Docker и /opt/marketplace.

Оба репозитория обновляют один общий стек по образам из серверного .env.
Traefik обновляется отдельно вручную. Команд Prisma нет: auth/catalog
используют Flyway при старте Spring. Успешность Build гарантирует сборку;
готовность HTTP API каталога проверяется отдельно после его исправления.

## Первая подготовка VPS

Команды ниже выполняются вручную. Не запускать первый деплой поверх старой
Compose-базы: сначала сделать резервную копию и остановить старые контейнеры.

На VPS подготовить каталог:

```bash
sudo mkdir -p /opt/marketplace/infra/traefik
sudo chown -R krefature:krefature /opt/marketplace
```

На Mac из корня backend-репозитория передать infra:

```bash
scp -i ~/.ssh/cloudru_vm -r infra krefature@87.242.102.190:/opt/marketplace/
```

На VPS создать рабочие файлы:

```bash
cp -n /opt/marketplace/infra/.env.example /opt/marketplace/.env
chmod 600 /opt/marketplace/.env
cp /opt/marketplace/infra/app-stack.yml /opt/marketplace/app-stack.yml
nano /opt/marketplace/.env
```

Заполнить POSTGRES_PASSWORD существующим паролем БД; сохранить текущие
POSTGRES_USER и POSTGRES_DB. AUTH_JWT_SECRET заполнить один раз, например
результатом `openssl rand -hex 32`. Новый JWT secret потребует повторного входа
пользователей. Пароли не коммитить. CATALOG_REPLICAS оставить равным 0.

В рабочем .env образы по умолчанию используют latest. Если пока опубликованы
только SHA-теги, до первой успешной новой сборки указать существующие SHA-теги
в AUTH_IMAGE, CATALOG_IMAGE и FRONTEND_IMAGE.

Включить Swarm, если он ещё inactive, и создать внешние overlay-сети один раз:

```bash
docker info --format '{{.Swarm.LocalNodeState}}'
docker swarm init --advertise-addr 127.0.0.1 --listen-addr 127.0.0.1:2377
docker network create --driver overlay traefik-public
docker network create --driver overlay marketplace_egress-net
```

На единственной VPS кластерные порты 2377/7946/4789 не должны быть доступны
из интернета; для сайта нужны TCP 80/443, для доступа к VPS — SSH.
Том `marketplace-backend_postgres-data` уже должен существовать: app-stack
использует его как external, а не создаёт новую пустую БД. Сохраняется PostgreSQL
17, соответствующий текущим данным. Эта конфигурация рассчитана на одну VPS.

Сначала проверить доступ к registry и наличие всех образов:

```bash
set -a; source /opt/marketplace/.env; set +a
docker pull "$AUTH_IMAGE"
docker pull "$CATALOG_IMAGE"
docker pull "$FRONTEND_IMAGE"
docker pull postgres:17-alpine
docker pull traefik:v2.11
```

Для закрытых GHCR-пакетов предварительно выполнить docker login ghcr.io
пользователем krefature с правом чтения пакетов. --with-registry-auth использует
эту авторизацию. Даже при наличии локального образа --resolve-image always
обращается в registry: сеть VPS должна работать.

## Резервная копия и первый запуск

На работающей старой БД:

```bash
mkdir -p -m 700 ~/marketplace-backups
backup="$HOME/marketplace-backups/before-swarm-$(date +%Y%m%d-%H%M%S).dump"
docker exec marketplace-backend-db-1 pg_dump -U marketplace -d marketplace -Fc > "$backup"
chmod 600 "$backup"
docker exec -i marketplace-backend-db-1 pg_restore --list < "$backup"
```

Для проверки восстановления создать отдельный контейнер с собственным
временным томом, без подключения к production volume и без сети:

```bash
docker run -d --name marketplace-restore-check --network none \
  -e POSTGRES_USER=marketplace -e POSTGRES_DB=marketplace \
  -e POSTGRES_HOST_AUTH_METHOD=trust postgres:17-alpine
docker exec marketplace-restore-check pg_isready -U marketplace -d marketplace
```

После accepting connections выполнить:

```bash
docker exec -i marketplace-restore-check pg_restore \
  --no-owner --exit-on-error -U marketplace -d marketplace < "$backup"
docker rm -fv marketplace-restore-check
```

Когда образы скачаны и backup проверен, переключить контейнеры и порт 80:

```bash
docker update --restart=no marketplace-backend-auth-1 marketplace-backend-catalog-1 marketplace-backend-db-1 marketplace-frontend
docker stop marketplace-backend-auth-1 marketplace-backend-catalog-1 marketplace-backend-db-1 marketplace-frontend
sudo systemctl stop nginx

docker stack deploy -c /opt/marketplace/infra/traefik-stack.yml traefik
set -a; source /opt/marketplace/.env; set +a
docker stack deploy -c /opt/marketplace/app-stack.yml \
  --with-registry-auth --resolve-image always --detach=false marketplace
```

Не запускать одновременно старую и новую PostgreSQL с одним томом данных.
Старые остановленные контейнеры сохранить до завершения проверки. Пока идёт
переход, прежние Compose workflows не должны запускаться параллельно: новый
Deploy заменяет их только на ветках, в которые внесены эти изменения.

## Проверка

```bash
docker stack services traefik
docker stack services marketplace
docker service logs --tail 100 traefik_traefik
docker service logs --tail 100 marketplace_auth
curl -I http://87.242.102.190.nip.io
curl -I https://87.242.102.190.nip.io
curl -i https://87.242.102.190.nip.io/api/v1/users/me
```

Ожидаются перенаправление HTTP на HTTPS, доступный frontend и 401 от users/me
без токена. Затем проверить вход и refresh в браузере. Catalog пока имеет
0 реплик; включить его через CATALOG_REPLICAS=1 после исправления бэкенда.
После успешного перехода отключить автозапуск прежнего host nginx:
`sudo systemctl disable nginx`.

Для возврата сначала остановить/удалить новый стек marketplace и дождаться
полной остановки его PostgreSQL, затем убрать Traefik с портов 80/443 и вернуть
старые Compose-контейнеры и host nginx. Не восстанавливать dump поверх работающей
БД. Swarm rollback возвращает сервисы, но не откатывает данные или SQL-миграции.
