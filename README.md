# Sistema de automação residencial (IoT)

Stack para controle de iluminação (bridge MQTT/Tuya), ar-condicionado via ESP8266 e painel web. Este repositório segue a mesma disciplina operacional do projeto **Unifique Controle Protocolo**: `.env` na raiz, `docker compose` para MySQL + backend + frontend, documentação de comandos centralizada e healthchecks.

## 1. Pré-requisitos

- Java 17+ e Maven 3.9+ (desenvolvimento local do backend)
- Node.js 20+ (frontend)
- Docker Desktop + Docker Compose v2

## 2. Subida rápida via Docker Compose

```powershell
copy .env.example .env
```

Edite `.env`: `MYSQL_*`, `JWT_SECRET` (≥32 caracteres), `MQTT_PASSWORD` (deve coincidir entre Mosquitto e backend), `ADMIN_USERNAME` / `ADMIN_PASSWORD` para o primeiro usuário em banco vazio (perfil `prod`).

```powershell
docker compose up --build -d
```

- Frontend: http://localhost:3000  
- Backend: http://localhost:8080  
- Swagger não faz parte do escopo mínimo; health do Spring: `/actuator/health/readiness`  
- MySQL no host: porta `${MYSQL_PORT:-3307}`  

Perfil opcional da imagem **tuya-mqtt** (requer `tuya-mqtt/devices.conf`):

```powershell
docker compose --profile tuya up --build -d
```

## 3. Desenvolvimento local (sem rebuild de imagens)

Fluxo recomendado:

1. `docker compose up -d mysql mosquitto`
2. Na raiz: `.\scripts\run-backend-local.ps1` (usa `.env` e sobe Spring em `sistema-esp8266/app`)
3. `cd frontend && npm install && npm run dev`

Variáveis do Next: copie [`frontend/.env.local.example`](frontend/.env.local.example) para `frontend/.env.local` se precisar sobrescrever algo. Deixe `NEXT_PUBLIC_API_URL` vazio para usar rewrites do Next em desenvolvimento.

Orquestração legada com Node-RED + bridge Node na raiz:

```powershell
npm install
npm start
```

## 4. Testes e qualidade

```powershell
cd frontend && npm test
cd sistema-esp8266\app && .\mvnw.cmd test
```

## 5. Estrutura do repositório

```
./
├── frontend/                 Next.js (App Router), proxy `/api` para Spring
├── sistema-esp8266/app/      Spring Boot + Flyway + MQTT
├── mosquitto/                Configuração do broker
├── tuya-mqtt/                Bridge opcional (Compose profile `tuya`)
├── node-red/                 Fluxos de desenvolvimento
├── firmware/                 Firmware ESP8266
├── docs/                     Scripts de banco + MANUAL operacional
├── scripts/run-backend-local.ps1
├── docker-compose.yml
├── .env.example
├── COMANDOS.md
└── README.md
```

## 6. Documentação adicional

- Comandos detalhados: [`COMANDOS.md`](COMANDOS.md)  
- Operação e componentes: [`docs/MANUAL.md`](docs/MANUAL.md)  
- Frontend: [`frontend/README.md`](frontend/README.md) — usar `.env.local.example` como referência  

## 7. Segurança

- Não commitar `.env`, segredos MQTT/Tuya ou `application-dev.properties` (use [`application-dev.properties.example`](sistema-esp8266/app/src/main/resources/application-dev.properties.example) como modelo).
- Gere `JWT_SECRET` com `openssl rand -base64 48`.
- Ajuste `CORS_ALLOWED_ORIGINS` para os hosts reais do frontend em produção.
