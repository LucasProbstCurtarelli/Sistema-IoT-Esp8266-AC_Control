# Sistema de Automação Residencial

Sistema completo de automação residencial com controle de dispositivos IoT via interface web.

## 🚀 Início Rápido

```bash
npm start
```

Isso inicia todos os serviços:
- **Docker** (Mosquitto MQTT)
- **Node-RED**
- **Spring Boot** (Backend API)
- **Frontend** (Next.js)
- **Tuya Bridge** (Bridge MQTT → Tuya)

## 📋 Pré-requisitos

- Node.js 18+
- Java 21+
- MySQL 8+
- Docker Desktop (para MQTT broker)

## ⚙️ Configuração

### Variáveis de Ambiente

O Spring Boot requer `JWT_SECRET` (mínimo 32 caracteres). Para desenvolvimento, já está configurado no `package.json`.

Para produção, configure:
```bash
export JWT_SECRET="seu-secret-aqui-minimo-32-caracteres"
```

### Banco de Dados

**Primeira vez ou reset completo:**
1. Execute o script `docs/database/init_database.sql` no MySQL Workbench
2. Isso cria o banco do zero com todas as tabelas e dados iniciais

**Migrações automáticas:**
O Flyway gerencia migrações futuras automaticamente. As migrações estão em:
- `sistema-esp8266/app/src/main/resources/db/migration/`

## 📁 Estrutura

```
├── frontend/              # Interface Next.js
├── sistema-esp8266/       # Backend Spring Boot
├── node-red/              # Node-RED flows e configuração
├── mosquitto/             # Configuração MQTT broker
├── tuya-mqtt/             # Bridge MQTT → Tuya e configuração
├── firmware/              # Firmware Arduino/ESP8266
├── docs/                  # Documentação e scripts de banco
├── docker-compose.yml     # Configuração Docker
└── package.json           # Scripts de inicialização
```

## 🔐 Credenciais Padrão (Desenvolvimento)

- **Usuário:** admin (5 caracteres - válido)
- **Senha:** admin123 (8 caracteres - válido)

⚠️ **Altere em produção usando variáveis de ambiente:**
- `ADMIN_USERNAME` (5-25 caracteres)
- `ADMIN_PASSWORD` (7-25 caracteres)
