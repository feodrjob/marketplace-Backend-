# Деплой сборкой на VM

Workflow: `.github/workflows/deploy.yml`. CI (`ci.yml`) не изменён.

## Запуск

1. Изменения готовятся в `fix/deploy-vm`. Новая схема начнёт работать после слияния в `main`: GitHub использует workflow `workflow_run` из default branch.
2. Push в `main` запускает существующий Backend CI. После его успешного завершения workflow подключается к VM по SSH. Проверки PR не запускают деплой.
3. VM получает `origin/main`, сверяет вершину ветки с SHA завершившегося CI, переключается на `main` через fast-forward, собирает Docker-образы и запускает Compose с ожиданием healthcheck. Устаревший CI пропускается.

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

Обновление выполняется через `git merge --ff-only`, затем одной командой `docker compose up --build --remove-orphans --wait`. Автоматического отката нет. `deploy/package.py` и `deploy/install.sh` остались как файлы прежней схемы, новый workflow их не вызывает.
