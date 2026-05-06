# Comandos úteis

Referência na raiz do repositório. Variáveis: [`.env.example`](./.env.example) e [`README.md`](./README.md).

---

## 1. Primeira vez

```powershell
copy .env.example .env
```

Edite `.env` — obrigatório definir pelo menos `MYSQL_*`, `JWT_SECRET`, `MQTT_PASSWORD` (o mesmo valor no Mosquitto e no backend) e credenciais de admin inicial (`ADMIN_*`) quando usar perfil `prod`.

Instalar dependências do frontend:

```powershell
cd frontend
npm install
cd ..
```

---

## 2. Stack Docker (recomendado)

Na raiz, com `.env` configurado:

```powershell
docker compose up --build -d
```

- Frontend: http://localhost:3000  
- Backend API: http://localhost:8080  
- Readiness Spring: http://localhost:8080/actuator/health/readiness  
- MySQL no host: `localhost:${MYSQL_PORT}` (padrão **3307**)  

Bridge opcional Tuya (imagem externa + `devices.conf`):

```powershell
docker compose --profile tuya up --build -d
```

Parar:

```powershell
docker compose down
```

Zerar volume do MySQL:

```powershell
docker compose down -v
```

---

## 3. Desenvolvimento rápido (sem rebuild de imagens)

1. Subir só o MySQL (e Mosquitto, se precisar de MQTT):

   ```powershell
   docker compose up -d mysql mosquitto
   ```

2. Backend na máquina (PowerShell na raiz — carrega `.env`):

   ```powershell
   .\scripts\run-backend-local.ps1
   ```

3. Frontend com hot reload:

   ```powershell
   cd frontend
   npm run dev
   ```

Com `NEXT_PUBLIC_API_URL` vazio em `.env.local`, o Next reescreve `/api/*` para `http://127.0.0.1:8080` (veja `frontend/next.config.ts`).

---

## 4. Orquestração completa para desenvolvimento (opcional)

O [`package.json`](./package.json) na raiz ainda pode subir Node-RED + MQTT + bridge via `npm start` quando você precisa desse fluxo legado — consulte o README.

Testes do frontend:

```powershell
cd frontend
npm test
```

Backend:

```powershell
cd sistema-esp8266\app
.\mvnw.cmd test
```

---

## 5. Resumo

| Objetivo | Comando |
|----------|---------|
| Subir stack principal | `docker compose up --build -d` |
| Subir com perfil Tuya | `docker compose --profile tuya up --build -d` |
| Parar stack | `docker compose down` |
| Backend local | `.\scripts\run-backend-local.ps1` |
| Frontend dev | `cd frontend && npm run dev` |
| Testes UI | `cd frontend && npm test` |
