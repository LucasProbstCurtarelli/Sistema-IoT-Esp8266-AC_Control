#!/usr/bin/env node

/**
 * Tuya MQTT Bridge
 * 
 * Bridge que escuta comandos MQTT e envia diretamente para as lâmpadas Tuya.
 * Substitui o Docker tuya-mqtt que não está funcionando.
 * 
 * Uso:
 *   node tuya-mqtt-bridge.js
 * 
 * Configuração:
 *   Copie tuya-config.example.js para tuya-config.js e preencha suas credenciais.
 * 
 * Este script deve rodar em background enquanto o sistema está ativo.
 */

const mqtt = require('mqtt');
const TuyAPI = require('tuyapi');
const path = require('path');
const fs = require('fs');

// Load configuration from tuya-config.js
const configPath = path.join(__dirname, 'tuya-config.js');

if (!fs.existsSync(configPath)) {
    console.error('❌ Arquivo de configuração não encontrado!');
    console.error('   Por favor, copie tuya-config.example.js para tuya-config.js');
    console.error('   e preencha suas credenciais de dispositivos Tuya.');
    process.exit(1);
}

const config = require('./tuya-config.js');

const MQTT_BROKER = process.env.MQTT_BROKER || config.MQTT_BROKER || 'mqtt://127.0.0.1:1883';
const MQTT_USERNAME = process.env.MQTT_USERNAME || config.MQTT_USERNAME || '';
const MQTT_PASSWORD = process.env.MQTT_PASSWORD || config.MQTT_PASSWORD || '';
const COMMAND_DEBOUNCE_MS = config.COMMAND_DEBOUNCE_MS || 200;

function ipsFromDevicesConf() {
    const filePath = path.join(__dirname, 'devices.conf');
    if (!fs.existsSync(filePath)) {
        return {};
    }
    const ips = {};
    let section = null;
    for (const rawLine of fs.readFileSync(filePath, 'utf8').split(/\r?\n/)) {
        const line = rawLine.trim();
        const header = line.match(/^\[([^\]]+)\]$/);
        if (header) {
            section = header[1].trim();
            continue;
        }
        if (!section || !line || line.startsWith('#')) {
            continue;
        }
        const match = line.match(/^ip\s*[:=]\s*(\S+)/i);
        if (match) {
            ips[section] = match[1];
        }
    }
    return ips;
}

const knownIps = ipsFromDevicesConf();

// Initialize devices from configuration
const DEVICES = {};
for (const [deviceName, deviceConfig] of Object.entries(config.DEVICES)) {
    DEVICES[deviceName] = {
        id: deviceConfig.id,
        key: deviceConfig.key,
        version: deviceConfig.version || '3.3',
        ip: deviceConfig.ip || knownIps[deviceName] || null,
        device: null,
        lastCommandTime: 0,
        pendingCommand: null
    };
}

let mqttClient = null;

/**
 * Conecta à lâmpada Tuya
 */
async function connectDevice(deviceName, deviceConfig) {
    if (deviceConfig.device && deviceConfig.device.isConnected()) {
        return true;
    }

    console.log(`[Bridge] Conectando à lâmpada ${deviceName} (${deviceConfig.id})...`);
    
    const options = {
        id: deviceConfig.id,
        key: deviceConfig.key,
        version: deviceConfig.version
    };

    if (deviceConfig.ip) {
        options.ip = deviceConfig.ip;
    }

    const device = new TuyAPI(options);

    device.on('connected', () => {
        console.log(`[Bridge] ✅ Conectado à lâmpada ${deviceName}`);
    });

    device.on('disconnected', () => {
        console.log(`[Bridge] ❌ Desconectado da lâmpada ${deviceName}`);
        // Limpa referência mas mantém IP para reconexão rápida
        deviceConfig.device = null;
    });

    device.on('error', (error) => {
        console.error(`[Bridge] ❌ Erro na lâmpada ${deviceName}:`, error.message);
    });
    
    device.on('data', (data) => {
        console.log(`[Bridge] 📥 Dados recebidos da lâmpada ${deviceName}:`, JSON.stringify(data, null, 2));
    });

    try {
        if (!deviceConfig.ip) {
            const ip = await device.find();
            deviceConfig.ip = ip;
            console.log(`[Bridge] IP da lâmpada ${deviceName} descoberto: ${ip}`);
        }
        
        await device.connect();
        deviceConfig.device = device;
        return true;
    } catch (error) {
        console.error(`[Bridge] ❌ Erro ao conectar à lâmpada ${deviceName}:`, error.message);
        return false;
    }
}

/**
 * Conecta todas as lâmpadas
 */
async function connectAllDevices() {
    console.log('\n[Bridge] Conectando às lâmpadas...\n');
    
    for (const [deviceName, config] of Object.entries(DEVICES)) {
        await connectDevice(deviceName, config);
        // Aguarda um pouco entre conexões
        await new Promise(resolve => setTimeout(resolve, 1000));
    }
}

/**
 * Converte cor Tuya (HHHHSSSSVVVV) para RGB hex (#RRGGBB)
 */
function tuyaColorHexToRgbHex(tuyaHex, fullValue) {
    if (!tuyaHex || tuyaHex.length !== 12) {
        return '#FFFFFF'; // Default white
    }
    
    // Extrai HSV do formato Tuya
    const hue = parseInt(tuyaHex.substring(0, 4), 16);        // 0-360
    const saturation = parseInt(tuyaHex.substring(4, 8), 16); // 0-1000
    const value = parseInt(tuyaHex.substring(8, 12), 16);     // 0-1000
    
    // Normaliza para 0-1. No modo cor o brilho vai à parte, senão a amostra fica escura.
    const h = hue / 360.0;
    const s = saturation / 1000.0;
    const v = fullValue ? 1 : value / 1000.0;
    
    // Converte HSV para RGB
    const c = v * s;
    const x = c * (1 - Math.abs((h * 6) % 2 - 1));
    const m = v - c;
    
    let r, g, b;
    if (h < 1/6) {
        r = c; g = x; b = 0;
    } else if (h < 2/6) {
        r = x; g = c; b = 0;
    } else if (h < 3/6) {
        r = 0; g = c; b = x;
    } else if (h < 4/6) {
        r = 0; g = x; b = c;
    } else if (h < 5/6) {
        r = x; g = 0; b = c;
    } else {
        r = c; g = 0; b = x;
    }
    
    // Converte para 0-255 e formata como hex
    const rInt = Math.round((r + m) * 255);
    const gInt = Math.round((g + m) * 255);
    const bInt = Math.round((b + m) * 255);
    
    return '#' + 
        rInt.toString(16).padStart(2, '0') +
        gInt.toString(16).padStart(2, '0') +
        bInt.toString(16).padStart(2, '0');
}

/**
 * Converte estado Tuya (DPS) para formato legível
 */
function convertTuyaStateToResponse(tuyaState) {
    // Valida entrada
    if (!tuyaState || typeof tuyaState !== 'object') {
        throw new Error('Invalid tuyaState: expected object but got ' + typeof tuyaState);
    }
    
    // TuyAPI pode retornar dps diretamente ou dentro de um objeto
    // Tenta diferentes formatos possíveis
    let dps = {};
    
    if (tuyaState.dps && typeof tuyaState.dps === 'object') {
        // Formato padrão: { dps: { '20': true, '22': 500, ... } }
        dps = tuyaState.dps;
    } else if (tuyaState['20'] !== undefined || tuyaState['21'] !== undefined || tuyaState['22'] !== undefined) {
        // Formato direto: { '20': true, '22': 500, ... }
        dps = tuyaState;
    } else {
        // Tenta acessar propriedades numéricas como strings
        const keys = Object.keys(tuyaState);
        const numericKeys = keys.filter(k => /^\d+$/.test(k));
        if (numericKeys.length > 0) {
            dps = tuyaState;
        } else {
            console.warn(`[Bridge] Estado Tuya não tem formato DPS esperado:`, tuyaState);
            // Retorna valores padrão se não conseguir extrair DPS
            return {
                state: false,
                brightness: 100,
                color: '#FFFFFF',
                mode: 'white'
            };
        }
    }
    
    // DPS 20: Power state
    const state = dps['20'] === true || dps['20'] === 1 || dps['20'] === 'true';
    
    // DPS 22: Brightness (10-1000) -> (0-100)
    let brightness = 100;
    if (dps['22'] !== undefined && dps['22'] !== null) {
        const tuyaBrightness = parseInt(dps['22']);
        if (!isNaN(tuyaBrightness)) {
            brightness = Math.round(((tuyaBrightness - 10) / 990) * 100);
            brightness = Math.max(0, Math.min(100, brightness));
        }
    }
    
    // DPS 21: Mode. White mode ignores DPS 24, so the swatch must not show that leftover color.
    const mode = dps['21'] || 'white';
    const colourMode = mode === 'colour';

    // DPS 24: Color (HHHHSSSSVVVV) -> (#RRGGBB)
    let color = '#FFFFFF';
    if (colourMode && dps['24'] && typeof dps['24'] === 'string') {
        try {
            color = tuyaColorHexToRgbHex(dps['24'], true);
        } catch (e) {
            console.warn(`[Bridge] Erro ao converter cor ${dps['24']}:`, e.message);
        }
    }
    
    return {
        state,
        brightness,
        color,
        mode
    };
}

/**
 * Obtém estado do dispositivo aguardando evento 'data'
 * O TuyAPI não retorna diretamente de get(), os dados chegam via evento
 */
function getDeviceState(device) {
    return new Promise((resolve, reject) => {
        let resolved = false;
        
        const timeout = setTimeout(() => {
            if (!resolved) {
                resolved = true;
                device.removeListener('data', dataHandler);
                reject(new Error('Timeout waiting for device state'));
            }
        }, 5000);
        
        const dataHandler = (data) => {
            if (!resolved) {
                resolved = true;
                clearTimeout(timeout);
                device.removeListener('data', dataHandler);
                resolve(data);
            }
        };
        
        // Adiciona listener ANTES de chamar get()
        // Usa prependOnceListener para garantir que seja chamado antes do listener global
        device.prependOnceListener('data', dataHandler);
        
        // Chama get() para solicitar estado
        device.get().catch((error) => {
            if (!resolved) {
                resolved = true;
                clearTimeout(timeout);
                device.removeListener('data', dataHandler);
                reject(error);
            }
        });
    });
}

/**
 * Processa query de estado MQTT e consulta a lâmpada
 */
async function handleStateQuery(deviceName, queryPayload) {
    const deviceConfig = DEVICES[deviceName];
    
    if (!deviceConfig) {
        console.error(`[Bridge] ❌ Dispositivo desconhecido: ${deviceName}`);
        const errorResponse = {
            correlationId: queryPayload.correlationId,
            error: 'Device not found',
            timestamp: Date.now()
        };
        mqttClient.publish(`tuya/${deviceName}/state`, JSON.stringify(errorResponse));
        return;
    }
    
    // Garante que está conectado
    if (!deviceConfig.device) {
        console.log(`[Bridge] Conectando à lâmpada ${deviceName} para consulta...`);
        const connected = await connectDevice(deviceName, deviceConfig);
        if (!connected) {
            console.error(`[Bridge] ❌ Não foi possível conectar à lâmpada ${deviceName}`);
            const errorResponse = {
                correlationId: queryPayload.correlationId,
                error: 'Device not connected',
                timestamp: Date.now()
            };
            mqttClient.publish(`tuya/${deviceName}/state`, JSON.stringify(errorResponse));
            return;
        }
    }
    
    // Verifica se dispositivo está conectado
    if (!deviceConfig.device.isConnected()) {
        console.log(`[Bridge] Dispositivo desconectado, tentando reconectar à lâmpada ${deviceName}...`);
        // Limpa referência e reconecta com nova instância
        deviceConfig.device = null;
        const connected = await connectDevice(deviceName, deviceConfig);
        if (!connected) {
            console.error(`[Bridge] ❌ Não foi possível reconectar à lâmpada ${deviceName}`);
            const errorResponse = {
                correlationId: queryPayload.correlationId,
                error: 'Device not connected',
                timestamp: Date.now()
            };
            mqttClient.publish(`tuya/${deviceName}/state`, JSON.stringify(errorResponse));
            return;
        }
    }
    
    try {
        console.log(`[Bridge] 📊 Consultando estado da lâmpada ${deviceName}...`);
        
        // Obtém estado aguardando evento 'data'
        const tuyaState = await getDeviceState(deviceConfig.device);
        
        // Debug: log do estado bruto recebido
        console.log(`[Bridge] Estado bruto recebido de ${deviceName}:`, JSON.stringify(tuyaState, null, 2));
        
        // Valida se o estado foi retornado
        if (!tuyaState) {
            throw new Error('Device returned null or undefined state');
        }
        
        // Converte para formato legível
        const response = convertTuyaStateToResponse(tuyaState);
        response.correlationId = queryPayload.correlationId;
        response.timestamp = Date.now();
        
        // Publica resposta
        const stateTopic = `tuya/${deviceName}/state`;
        mqttClient.publish(stateTopic, JSON.stringify(response), { qos: 1 });
        
        console.log(`[Bridge] ✅ Estado consultado e publicado para ${deviceName}:`, response);
        
    } catch (error) {
        console.error(`[Bridge] ❌ Erro ao consultar estado da lâmpada ${deviceName}:`, error.message);
        console.error(`[Bridge] Stack trace:`, error.stack);
        
        const errorResponse = {
            correlationId: queryPayload.correlationId,
            error: error.message,
            timestamp: Date.now()
        };
        
        mqttClient.publish(`tuya/${deviceName}/state`, JSON.stringify(errorResponse));
    }
}

/**
 * A Elgin volta ao branco se o brilho (DPS 22) chega no mesmo pacote que a cor.
 * Modo, cor e brilho vão separados, e a cor é repetida depois do brilho.
 */
async function sendSequentialDps(device, data) {
    const hasMode = data['21'];
    const hasColor = data['24'];
    const hasBrightness = data['22'];
    const hasPower = data['20'];

    if (hasPower !== undefined && (hasMode || hasColor || hasBrightness)) {
        await device.set({ dps: 20, set: hasPower });
        await new Promise(resolve => setTimeout(resolve, 100));
    }

    if (hasMode) {
        await device.set({ dps: 21, set: hasMode });
        await new Promise(resolve => setTimeout(resolve, 100));
    }

    if (hasColor) {
        await device.set({ dps: 24, set: hasColor });
        await new Promise(resolve => setTimeout(resolve, 50));
    }

    if (hasBrightness !== undefined && hasBrightness !== null) {
        await device.set({ dps: 22, set: hasBrightness });
        if (hasMode === 'colour' && hasColor) {
            await new Promise(resolve => setTimeout(resolve, 500));
            await device.set({ dps: 21, set: 'colour' });
            await new Promise(resolve => setTimeout(resolve, 100));
            await device.set({ dps: 24, set: hasColor });
            await new Promise(resolve => setTimeout(resolve, 100));
        }
    }

    if (hasPower !== undefined && !hasMode && !hasColor && (hasBrightness === undefined || hasBrightness === null)) {
        await device.set({ dps: 20, set: hasPower });
    }
}

/**
 * Processa comando MQTT e envia para a lâmpada
 */
async function processCommand(deviceName, payload) {
    const deviceConfig = DEVICES[deviceName];
    
    if (!deviceConfig) {
        console.error(`[Bridge] ❌ Dispositivo desconhecido: ${deviceName}`);
        return;
    }

    // Debounce: se um comando foi enviado recentemente, aguarda um pouco
    const now = Date.now();
    const timeSinceLastCommand = now - deviceConfig.lastCommandTime;
    
    if (timeSinceLastCommand < COMMAND_DEBOUNCE_MS) {
        const waitTime = COMMAND_DEBOUNCE_MS - timeSinceLastCommand;
        console.log(`[Bridge] ⏳ Aguardando ${waitTime}ms para evitar conflito de comandos...`);
        await new Promise(resolve => setTimeout(resolve, waitTime));
    }
    
    deviceConfig.lastCommandTime = Date.now();

    // Garante que está conectado
    if (!deviceConfig.device || !deviceConfig.device.isConnected()) {
        if (!deviceConfig.device) {
            console.log(`[Bridge] Conectando à lâmpada ${deviceName}...`);
        } else {
            console.log(`[Bridge] Dispositivo desconectado, reconectando à lâmpada ${deviceName}...`);
            deviceConfig.device = null;
        }
        const connected = await connectDevice(deviceName, deviceConfig);
        if (!connected) {
            console.error(`[Bridge] ❌ Não foi possível conectar à lâmpada ${deviceName}`);
            return;
        }
    }

    try {
        console.log(`[Bridge] 📤 Enviando comando para ${deviceName}:`, JSON.stringify(payload));
        
        // O payload já está no formato DPS correto do Spring Boot
        // Precisamos converter para o formato que o TuyAPI aceita
        
        const dpsData = (payload.multiple && payload.data) ? payload.data : payload.dps;
        if (dpsData && typeof dpsData === 'object') {
            const dpsKeys = Object.keys(dpsData);
            console.log(`[Bridge] Processando DPS:`, dpsData, `(${dpsKeys.length} DPS)`);

            if (dpsKeys.length > 1) {
                await sendSequentialDps(deviceConfig.device, dpsData);
            } else if (dpsKeys.length === 1) {
                const dpsKey = dpsKeys[0];
                await deviceConfig.device.set({
                    dps: parseInt(dpsKey, 10),
                    set: dpsData[dpsKey]
                });
            }
        } else {
            console.error(`[Bridge] ❌ Formato de payload inválido para ${deviceName}:`, payload);
            return;
        }
        
        console.log(`[Bridge] ✅ Comando enviado com sucesso para ${deviceName}\n`);
        
    } catch (error) {
        console.error(`[Bridge] ❌ Erro ao enviar comando para ${deviceName}:`, error.message);
        console.error(`[Bridge] Stack:`, error.stack);
        
        // Try to reconnect on next command
        deviceConfig.device = null;
    }
}

/**
 * Inicializa o bridge MQTT
 */
function startBridge() {
    console.log('\n' + '='.repeat(60));
    console.log('🌉 TUYA MQTT BRIDGE');
    console.log('='.repeat(60));
    console.log(`Conectando ao broker MQTT: ${MQTT_BROKER}`);
    console.log(`MQTT user: ${MQTT_USERNAME || '(nenhum)'}`);
    console.log(`Dispositivos configurados: ${Object.keys(DEVICES).join(', ')}`);
    for (const [name, device] of Object.entries(DEVICES)) {
        console.log(`[Bridge] ${name}: ${device.ip ? 'IP fixo' : 'sem IP, vai procurar na rede'}`);
    }
    console.log('');

    const mqttOptions = {
        clientId: 'tuya-mqtt-bridge',
        clean: true,
        reconnectPeriod: 1000
    };
    if (MQTT_USERNAME) {
        mqttOptions.username = MQTT_USERNAME;
        mqttOptions.password = MQTT_PASSWORD;
    }

    mqttClient = mqtt.connect(MQTT_BROKER, mqttOptions);

    mqttClient.on('connect', async () => {
        console.log('✅ Conectado ao broker MQTT\n');
        
        // Conecta às lâmpadas
        await connectAllDevices();
        
        // Subscribe nos tópicos de comando
        const commandTopics = Object.keys(DEVICES).map(name => `tuya/${name}/command`);
        
        commandTopics.forEach(topic => {
            mqttClient.subscribe(topic, { qos: 1 }, (err) => {
                if (err) {
                    console.error(`❌ Erro ao subscrever em ${topic}:`, err);
                } else {
                    console.log(`📡 Escutando tópico: ${topic}`);
                }
            });
        });
        
        // Subscribe nos tópicos de query (consulta de estado)
        mqttClient.subscribe('tuya/+/query', { qos: 1 }, (err) => {
            if (err) {
                console.error(`❌ Erro ao subscrever em tuya/+/query:`, err);
            } else {
                console.log(`📡 Escutando tópico: tuya/+/query (consultas de estado)`);
            }
        });
        
        console.log('\n✅ Bridge iniciado e pronto para receber comandos e consultas!\n');
    });

    mqttClient.on('error', (err) => {
        console.error('❌ Erro na conexão MQTT:', err);
    });

    mqttClient.on('message', async (topic, message) => {
        try {
            const payload = JSON.parse(message.toString());
            const topicParts = topic.split('/');
            const deviceName = topicParts[1]; // tuya/lampada_1/command -> lampada_1
            const messageType = topicParts[2]; // command, query, state
            
            if (messageType === 'query') {
                // Consulta de estado
                console.log(`\n📥 [${new Date().toISOString()}] Query de estado recebida:`);
                console.log(`   Tópico: ${topic}`);
                console.log(`   Device: ${deviceName}`);
                console.log(`   Correlation ID: ${payload.correlationId}`);
                
                await handleStateQuery(deviceName, payload);
            } else if (messageType === 'command') {
                // Comando de controle
                console.log(`\n📥 [${new Date().toISOString()}] Comando recebido:`);
                console.log(`   Tópico: ${topic}`);
                console.log(`   Device: ${deviceName}`);
                console.log(`   Payload: ${message.toString()}`);
                
                await processCommand(deviceName, payload);
            }
            // Ignora mensagens de state (são respostas que não precisam ser processadas aqui)
            
        } catch (error) {
            console.error(`❌ Erro ao processar mensagem do tópico ${topic}:`, error.message);
        }
    });

    mqttClient.on('close', () => {
        console.log('\n🔌 Conexão MQTT fechada');
    });
}

/**
 * Função principal
 */
async function main() {
    console.log('🚀 Iniciando Tuya MQTT Bridge...\n');
    
    startBridge();
    
    // Mantém o script rodando
    process.on('SIGINT', () => {
        console.log('\n\n🛑 Encerrando bridge...');
        
        // Desconecta todas as lâmpadas
        Object.values(DEVICES).forEach(config => {
            if (config.device) {
                try {
                    config.device.disconnect();
                } catch (e) {
                    // Ignora erros ao desconectar
                }
            }
        });
        
        if (mqttClient) {
            mqttClient.end();
        }
        
        console.log('✅ Bridge encerrado');
        process.exit(0);
    });
}

main().catch(console.error);
