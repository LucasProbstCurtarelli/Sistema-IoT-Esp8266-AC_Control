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
 * Este script deve rodar em background enquanto o sistema está ativo.
 */

const mqtt = require('mqtt');
const TuyAPI = require('tuyapi');

const MQTT_BROKER = 'mqtt://localhost:1883';

// Configuração das lâmpadas (do devices.conf)
const DEVICES = {
    lampada_1: {
        id: 'REMOVED_DEVICE_ID_1',
        key: 'REMOVED_DEVICE_KEY_1',
        version: '3.3',
        ip: null, // Será descoberto automaticamente
        device: null,
        lastCommandTime: 0,
        pendingCommand: null
    },
    lampada_2: {
        id: 'REMOVED_DEVICE_ID_2',
        key: 'REMOVED_DEVICE_KEY_2',
        version: '3.3',
        ip: null,
        device: null,
        lastCommandTime: 0,
        pendingCommand: null
    }
};

let mqttClient = null;

// Debounce delay to prevent command conflicts (ms)
const COMMAND_DEBOUNCE_MS = 200;

/**
 * Conecta à lâmpada Tuya
 */
async function connectDevice(deviceConfig) {
    if (deviceConfig.device && deviceConfig.device.isConnected()) {
        return true;
    }

    console.log(`[Bridge] Conectando à lâmpada ${deviceConfig.id}...`);
    
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
        console.log(`[Bridge] ✅ Conectado à lâmpada ${deviceConfig.id}`);
    });

    device.on('disconnected', () => {
        console.log(`[Bridge] ❌ Desconectado da lâmpada ${deviceConfig.id}`);
    });

    device.on('error', (error) => {
        console.error(`[Bridge] ❌ Erro na lâmpada ${deviceConfig.id}:`, error.message);
    });

    try {
        if (!deviceConfig.ip) {
            const ip = await device.find();
            deviceConfig.ip = ip;
            console.log(`[Bridge] IP da lâmpada ${deviceConfig.id} descoberto: ${ip}`);
        }
        
        await device.connect();
        deviceConfig.device = device;
        return true;
    } catch (error) {
        console.error(`[Bridge] ❌ Erro ao conectar à lâmpada ${deviceConfig.id}:`, error.message);
        return false;
    }
}

/**
 * Conecta todas as lâmpadas
 */
async function connectAllDevices() {
    console.log('\n[Bridge] Conectando às lâmpadas...\n');
    
    for (const [deviceName, config] of Object.entries(DEVICES)) {
        await connectDevice(config);
        // Aguarda um pouco entre conexões
        await new Promise(resolve => setTimeout(resolve, 1000));
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
    if (!deviceConfig.device) {
        console.log(`[Bridge] Conectando à lâmpada ${deviceName}...`);
        const connected = await connectDevice(deviceConfig);
        if (!connected) {
            console.error(`[Bridge] ❌ Não foi possível conectar à lâmpada ${deviceName}`);
            return;
        }
    }

    try {
        console.log(`[Bridge] 📤 Enviando comando para ${deviceName}:`, JSON.stringify(payload));
        
        // O payload já está no formato DPS correto do Spring Boot
        // Precisamos converter para o formato que o TuyAPI aceita
        
        if (payload.multiple && payload.data) {
            // Formato múltiplo: {"multiple": true, "data": {"20": true, "21": "colour", ...}}
            console.log(`[Bridge] Usando formato multiple com data:`, payload.data);
            
            // Some Tuya devices need commands sent sequentially, especially when changing mode
            // Send mode first, then color, then brightness, with small delays
            const hasMode = payload.data['21'];
            const hasColor = payload.data['24'];
            const hasBrightness = payload.data['22'];
            const hasPower = payload.data['20'];
            
            // Step 1: Set mode FIRST if present (critical for maintaining colour mode)
            if (hasMode) {
                console.log(`[Bridge] Step 1: Setting mode to ${hasMode}`);
                await deviceConfig.device.set({
                    dps: 21,
                    set: hasMode
                });
                // Small delay to ensure mode is set before other changes
                await new Promise(resolve => setTimeout(resolve, 100));
            }
            
            // Step 2: Set color if present
            if (hasColor) {
                console.log(`[Bridge] Step 2: Setting color to ${hasColor}`);
                await deviceConfig.device.set({
                    dps: 24,
                    set: hasColor
                });
                // Small delay
                await new Promise(resolve => setTimeout(resolve, 50));
            }
            
            // Step 3: Set brightness if present
            if (hasBrightness) {
                console.log(`[Bridge] Step 3: Setting brightness to ${hasBrightness}`);
                await deviceConfig.device.set({
                    dps: 22,
                    set: hasBrightness
                });
                
                // CRITICAL: Wait 1 second before re-confirming color to allow lamp to process brightness
                // Some Tuya devices need time to process brightness before accepting color commands
                console.log(`[Bridge] ⏳ Waiting 1 second before re-confirming color mode...`);
                await new Promise(resolve => setTimeout(resolve, 1000));
                
                // CRITICAL: Re-send mode "colour" AND color AFTER brightness to prevent reverting to white
                // Some Tuya devices revert to white mode when brightness is changed
                if (hasMode && hasMode === 'colour') {
                    console.log(`[Bridge] Step 4: Re-confirming mode 'colour' after brightness change`);
                    await deviceConfig.device.set({
                        dps: 21,
                        set: 'colour'
                    });
                    await new Promise(resolve => setTimeout(resolve, 200));
                    
                    // Also re-send color to ensure it's maintained
                    if (hasColor) {
                        console.log(`[Bridge] Step 5: Re-confirming color after brightness change`);
                        await deviceConfig.device.set({
                            dps: 24,
                            set: hasColor
                        });
                        await new Promise(resolve => setTimeout(resolve, 100));
                    }
                }
            }
            
            // Step 4: Set power if present (usually done first, but we do it last if mode/color/brightness are present)
            if (hasPower && !hasMode && !hasColor && !hasBrightness) {
                // Only set power alone if no other DPS are present
                console.log(`[Bridge] Setting power to ${hasPower}`);
                await deviceConfig.device.set({
                    dps: 20,
                    set: hasPower
                });
            } else if (hasPower && (hasMode || hasColor || hasBrightness)) {
                // If power is set with other DPS, set it first
                console.log(`[Bridge] Step 0: Setting power to ${hasPower} (before mode/color/brightness)`);
                await deviceConfig.device.set({
                    dps: 20,
                    set: hasPower
                });
                await new Promise(resolve => setTimeout(resolve, 100));
            }
            
            console.log(`[Bridge] ✅ Comandos sequenciais enviados com sucesso`);
        } else if (payload.dps) {
            // Formato DPS: {"dps": {"20": true}} ou {"dps": {"20": true, "22": 500}}
            const dpsKeys = Object.keys(payload.dps);
            console.log(`[Bridge] Processando DPS:`, payload.dps, `(${dpsKeys.length} DPS)`);
            
            if (dpsKeys.length > 1) {
                // Múltiplos DPS - usa formato multiple
                await deviceConfig.device.set({
                    multiple: true,
                    data: payload.dps
                });
            } else {
                // DPS único - formato direto
                const dpsKey = dpsKeys[0];
                const dpsValue = payload.dps[dpsKey];
                console.log(`[Bridge] DPS único: ${dpsKey} = ${dpsValue}`);
                await deviceConfig.device.set({
                    dps: parseInt(dpsKey),
                    set: dpsValue
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
    }
}

/**
 * Inicializa o bridge MQTT
 */
function startBridge() {
    console.log('\n' + '='.repeat(60));
    console.log('🌉 TUYA MQTT BRIDGE');
    console.log('='.repeat(60));
    console.log(`Conectando ao broker MQTT: ${MQTT_BROKER}\n`);
    
    mqttClient = mqtt.connect(MQTT_BROKER, {
        clientId: 'tuya-mqtt-bridge',
        clean: true,
        reconnectPeriod: 1000
    });

    mqttClient.on('connect', async () => {
        console.log('✅ Conectado ao broker MQTT\n');
        
        // Conecta às lâmpadas
        await connectAllDevices();
        
        // Subscribe nos tópicos de comando
        const topics = Object.keys(DEVICES).map(name => `tuya/${name}/command`);
        
        topics.forEach(topic => {
            mqttClient.subscribe(topic, { qos: 1 }, (err) => {
                if (err) {
                    console.error(`❌ Erro ao subscrever em ${topic}:`, err);
                } else {
                    console.log(`📡 Escutando tópico: ${topic}`);
                }
            });
        });
        
        console.log('\n✅ Bridge iniciado e pronto para receber comandos!\n');
    });

    mqttClient.on('error', (err) => {
        console.error('❌ Erro na conexão MQTT:', err);
    });

    mqttClient.on('message', async (topic, message) => {
        try {
            const payload = JSON.parse(message.toString());
            const deviceName = topic.split('/')[1]; // tuya/lampada_1/command -> lampada_1
            
            console.log(`\n📥 [${new Date().toISOString()}] Comando recebido:`);
            console.log(`   Tópico: ${topic}`);
            console.log(`   Device: ${deviceName}`);
            console.log(`   Payload: ${message.toString()}`);
            
            await processCommand(deviceName, payload);
            
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
