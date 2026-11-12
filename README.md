# Локальный запуск бэкенда Т-Маркета

Dockerfile собирает корневой `bootJar` приложения `MarketplaceBackendApplication` через Gradle Wrapper 9.8.2 на Java 21 и запускает его на JRE 21. Spring Boot в проекте зафиксирован на версии 4.1.2. Контейнер работает от UID `10001`, порт по умолчанию в образе — `8081`.

Корневой проект подключается к PostgreSQL. В текущем `main` модуль `auth` содержит каркас. Доменный код и миграция `auth.users` находятся в отдельных ветках `feat/auth-folder-structure` и `feat/auth-db`. Для проверки маршрутов Auth потребуется подключить эти слои и HTTP-контроллеры к запускаемому приложению.

## Сборка и запуск с PostgreSQL

Выполните из корня репозитория в PowerShell. Пароль для локальной БД генерируется на время запуска и передаётся контейнерам через переменные окружения.

```powershell
docker build -t t-market-auth:local .
docker network create t-market-local
$env:POSTGRES_PASSWORD = [guid]::NewGuid().ToString('N')
docker run -d --rm --name t-market-db --network t-market-local `
  -p 127.0.0.1:55432:5432 -e POSTGRES_DB=marketplace `
  -e POSTGRES_USER=marketplace -e POSTGRES_PASSWORD postgres:16-alpine
```

Дождитесь `accepting connections` от `docker exec t-market-db pg_isready -h 127.0.0.1 -U marketplace -d marketplace`. Затем запустите бэкенд.

```powershell
$env:SPRING_DATASOURCE_PASSWORD = $env:POSTGRES_PASSWORD
docker run -d --rm --name t-market-auth --network t-market-local `
  -p 127.0.0.1:8081:8081 `
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://t-market-db:5432/marketplace `
  -e SPRING_DATASOURCE_USERNAME=marketplace -e SPRING_DATASOURCE_PASSWORD `
  t-market-auth:local
docker logs t-market-auth 2>&1 | Select-String 'Start completed|Tomcat started|Started MarketplaceBackendApplication'
curl.exe -sS -o NUL -w '%{http_code}\n' http://127.0.0.1:8081/
```

После старта на `/` ожидается `401` от стандартной конфигурации Spring Security. Это проверка HTTP-порта. Если порт на хосте занят, измените левую часть привязки `8081:8081`. Порт внутри контейнера можно задать переменной `SERVER_PORT`, обновив правую часть привязки.

## Проверка Gradle

Используйте тот же PostgreSQL. Запущенный с хоста Gradle обращается к опубликованному порту `55432`.

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:55432/marketplace'
$env:SPRING_DATASOURCE_USERNAME = 'marketplace'
$env:SPRING_DATASOURCE_PASSWORD = $env:POSTGRES_PASSWORD
.\gradlew.bat --no-daemon clean build
```

После проверки выполните `docker stop t-market-auth t-market-db` и `docker network rm t-market-local`. Параметр `--rm` удалит тестовые контейнеры и анонимный том БД при остановке.
