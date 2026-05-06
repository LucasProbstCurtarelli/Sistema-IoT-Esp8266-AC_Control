# Manual operacional — Automação residencial IoT

Visão geral do sistema entregue por este repositório (MQTT, ESP8266, bridge Tuya, API Spring Boot, dashboard Next.js).

---

## 1. Papéis dos componentes

- **Next.js (`frontend/`)**: painel web (login, iluminação, ar-condicionado). Em Docker usa `standalone` e proxy `/api` para o backend.
- **Spring Boot (`sistema-esp8266/app/`)**: autenticação JWT + REST (`/api/devices`, `/api/lights`, `/api/ac`, …). Integração MQTT para comandos e leitura de estado.
- **MySQL**: dados de usuários, dispositivos e migrações Flyway.
- **Mosquitto**: broker MQTT usado pelo firmware, pela bridge Tuya e pelo backend.
- **Node-RED / bridge Node (`tuya-mqtt/`, raiz `npm start`)**: fluxos opcionais de desenvolvimento; documentados no README principal.

---

## 2. Subida em produção simplificada

1. Copiar [`.env.example`](../.env.example) para `.env` e preencher segredos (`JWT_SECRET`, senhas MySQL/MQTT, `ADMIN_*`).
2. Garantir schema inicial ou deixar Flyway criar/atualizar conforme migrações (`sistema-esp8266/app/src/main/resources/db/migration`).
3. `docker compose up --build -d`.
4. Acessar o frontend, alterar senha padrão do administrador e revisar `CORS_ALLOWED_ORIGINS` para o domínio real.

Healthchecks: frontend (`/api/health` no Next), backend (`/actuator/health/readiness`).

---

## 3. Segurança

- Nunca commitar `.env`, `application-dev.properties`, credenciais Tuya ou capturas de rede.
- Rotacione `JWT_SECRET` e senhas após qualquer vazamento presumido.
- Perfil `prod` exige senha de administrador forte na primeira criação de usuário.

---

## 4. Backup do MySQL

Use `docker compose exec mysql mysqldump` ou ferramentas GUI apontando para a porta publicada (`MYSQL_PORT`). Restaurar em ambiente limpo antes de subir uma nova versão da API se necessário.
