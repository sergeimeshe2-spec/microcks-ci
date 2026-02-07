# Microcks CI/CD

TeamCity configuration for Microcks Custom project.

## Структура

```
microcks-ci/
├── settings.kt           # TeamCity settings
├── project/
│   └── Project.kt        # Build configurations
└── README.md
```

## Build Configurations

### 1. Build
Maven сборка проекта microcks-custom.
- Автозапуск при каждом push в main
- Артефакты: JAR файлы

### 2. Docker
Сборка и пуш Docker образа.
- Запускается после успешного Build
- Пуш в ghcr.io/sergeimeshe2-spec/microcks-custom

### 3. Deploy
Деплой в Kubernetes через Helm.
- Ручной запуск
- Клонирует чарты из microcks-helm репы
- Деплой в namespace microcks

## Настройка в TeamCity

### 1. Импорт проекта

1. Открой TeamCity: http://localhost:8111
2. Создай новый проект
3. Выбери "From Kotlin DSL"
4. Загрузи файл `settings.kt`

### 2. Настройка VCS Root

**Repository URL**: `https://github.com/sergeimeshe2-spec/microcks-custom.git`

**Authentication**:
- Type: Password
- Username: `sergeimeshe2-spec`
- Password: GitHub PAT (credentials)

### 3. Настройка Credentials

В Project Settings создай credentials:

1. **GITHUB_TOKEN**
   - Type: JSON
   - Value: твой GitHub PAT

2. **DockerRegistryPassword**
   - Type: JSON
   - Value: твой GitHub PAT (для ghcr.io)

### 4. Настройка Agent

Agent должен иметь:
- Docker
- Java 21
- Maven 3.9
- kubectl
- Helm 3.x

### 5. Настройка Kubernetes

```bash
# Скопируй kubeconfig на agent
mkdir -p ~/.kube
scp your-k8s-server:~/.kube/config agent:~/.kube/config
```

## Запуск сборки

1. **Build**: Автоматически при push
2. **Docker**: Автоматически после Build
3. **Deploy**: Ручной запуск кнопкой "Run..."

## Мониторинг

- Build статуc: http://localhost:8111/app/rest/builds/statusIcon?buildTypeId=MicrocksCustom_Build
- Docker статус: http://localhost:8111/app/rest/builds/statusIcon?buildTypeId=MicrocksCustom_Docker
- Deploy статус: http://localhost:8111/app/rest/builds/statusIcon?buildTypeId=MicrocksCustom_Deploy

## Связанные репозитории

- **Core**: https://github.com/sergeimeshe2-spec/microcks-custom
- **Helm**: https://github.com/sergeimeshe2-spec/microcks-helm
- **Docker**: https://github.com/sergeimeshe2-spec/microcks-docker
- **Specs**: https://github.com/sergeimeshe2-spec/microcks-specs
