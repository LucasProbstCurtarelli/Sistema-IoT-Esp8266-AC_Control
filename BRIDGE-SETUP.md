# 🌉 Tuya MQTT Bridge - Setup

## ✅ Problema Resolvido

O backend Spring Boot está funcionando e enviando comandos MQTT corretamente, mas **não havia bridge** para converter MQTT → Tuya.

## 🔧 Solução Implementada

Criei um **bridge Node.js** (`tuya-mqtt-bridge.js`) que:
- ✅ Escuta tópicos MQTT `tuya/lampada_1/command` e `tuya/lampada_2/command`
- ✅ Converte payload DPS do Spring Boot para formato Tuya
- ✅ Envia comandos diretamente às lâmpadas usando `tuyapi`
- ✅ Reconecta automaticamente se desconectar

## 🚀 Como Usar

### Opção 1: Automático (Recomendado)

O bridge já está configurado para rodar automaticamente com `npm start`:

```bash
npm start
```

Isso iniciará:
- Docker (Mosquitto)
- Node-RED
- Spring Boot
- Frontend
- **Tuya Bridge** (novo!)

### Opção 2: Manual

Se quiser rodar apenas o bridge:

```bash
node tuya-mqtt-bridge.js
```

## 📊 Fluxo Completo

```
Frontend (Next.js)
    ↓ HTTP POST
Spring Boot API (/api/lights/lampada_1)
    ↓ MQTT Publish
Broker MQTT (localhost:1883)
    ↓ MQTT Subscribe
Tuya Bridge (tuya-mqtt-bridge.js)
    ↓ TuyAPI
Lâmpada Tuya (via protocolo local)
```

## 🔍 Verificação

Quando o bridge iniciar, você verá:

```
🌉 TUYA MQTT BRIDGE
============================================================
Conectando ao broker MQTT: mqtt://localhost:1883

✅ Conectado ao broker MQTT

[Bridge] Conectando às lâmpadas...

[Bridge] Conectando à lâmpada eb0e1c83cf8056eafdh1ke...
[Bridge] IP da lâmpada eb0e1c83cf8056eafdh1ke descoberto: 192.168.1.XXX
[Bridge] ✅ Conectado à lâmpada eb0e1c83cf8056eafdh1ke

📡 Escutando tópico: tuya/lampada_1/command
📡 Escutando tópico: tuya/lampada_2/command

✅ Bridge iniciado e pronto para receber comandos!
```

## ⚠️ Importante

- O bridge precisa descobrir o IP das lâmpadas na primeira execução
- Certifique-se de que as lâmpadas estão ligadas e na mesma rede Wi-Fi
- Se o IP mudar, o bridge tentará descobrir automaticamente

## 🎯 Próximos Passos

1. **Reinicie o sistema** com `npm start`
2. **Aguarde o bridge conectar** às lâmpadas
3. **Teste no frontend** - os botões devem funcionar agora!
