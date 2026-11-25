# Деплой сборкой на VM

Workflow: `.github/workflows/deploy.yml`. CI (`ci.yml`) не изменён.

## Запуск

1. Push в `main` напрямую запускает деплой: workflow подключается к VM по SSH, не дожидаясь Backend CI. Проверки PR не запускают деплой.
2. VM получает `origin/main`, сверяет вершину ветки с SHA push, переключается на `main` через fast-forward, собирает Docker-образы и запускает Compose с ожиданием healthcheck. Устаревший запуск пропускается.
3. Тесты остаются в отдельном Backend CI и не блокируют деплой. Dockerfile собирают `bootJar` без запуска тестов.

## Подготовка VM

- Linux, Git, Bash, Docker Engine и Docker Compose с поддержкой `up --wait`.
- Пользователь SSH может запускать Docker.
- Репозиторий клонирован в `~/marketplace-backend`; `origin` указывает на репозиторий бэкенда. VM имеет доступ к GitHub (для закрытого репозитория настройте отдельный read-only deploy key).
- В `~/marketplace-backend/.env` задан `POSTGRES_PASSWORD` и при необходимости порты из `compose.yaml`. `.env` игнорируется Git.
- На VM уже существует локальная ветка `main`, без собственных коммитов и изменений кода. Не редактируйте код в серверном checkout: он используется для деплоя.
- VM имеет доступ к Docker Hub, Gradle и Maven: сборка выполняется на ней. Cloud.ru registry в новой схеме не используется.
- Сохраняйте прежнее имя Compose-проекта и каталог: от них зависит выбор существующего PostgreSQL volume. Если предыдущий деплой задавал другое имя проекта, укажите `COMPOSE_PROJECT_NAME` в `.env` до запуска.

## Настройки GitHub

Secrets: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`.
Variable: `DEPLOY_PORT` (по умолчанию `22`).

`DEPLOY_KNOWN_HOSTS` содержит предварительно проверенный SSH host key VM. Секреты registry больше не нужны этому workflow.

Обновление выполняется через `git merge --ff-only`, затем отдельными шагами `docker compose --progress plain build` и `docker compose up --no-build --remove-orphans --wait`. В Actions отдельно видны подготовка SSH, обновление кода, сборка и запуск. Автоматического отката нет. `deploy/package.py` и `deploy/install.sh` остались как файлы прежней схемы, новый workflow их не вызывает.
