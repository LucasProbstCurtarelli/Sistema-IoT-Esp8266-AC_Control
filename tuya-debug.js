#!/usr/bin/env node

/**
 * Tuya MQTT Debug Script
 * 
 * Script para testar diferentes formatos de payload MQTT com a lâmpada Tuya Elgin A70.
 * 
 * Uso:
 *   node tuya-debug.js test-a    # Testa formato abstraído
 *   node tuya-debug.js test-b    # Testa formato DPS numérico V3.3
 *   node tuya-debug.js test-c    # Testa formato DPS nomeado
 *   node tuya-debug.js listen    # Apenas escuta mensagens (não envia comandos)
 */

const mqtt = require('mqtt');

const MQTT_BROKER = 'mqtt://localhost:1883';
const DEVICE_NAME = 'lampada_1';
const COMMAND_TOPIC = `tuya/${DEVICE_NAME}/command`;
const STATE_TOPIC = `tuya/${DEVICE_NAME}/state`;
const ALL_TUYA_TOPIC = 'tuya/#';

let client = null;
let isListening = false;
let lastSentPayload = null;  // Para filtrar ecos

/**
 * Conecta ao broker MQTT e configura listeners
 */
function connectMQTT() {
    console.log(`\n🔌 Conectando ao broker MQTT: ${MQTT_BROKER}`);
    
    client = mqtt.connect(MQTT_BROKER, {
        clientId: 'tuya-debug-script',
        clean: true,
        reconnectPeriod: 1000
    });

    client.on('connect', () => {
        console.log('✅ Conectado ao broker MQTT\n');
        
        // Subscribe no tópico de estado específico
        client.subscribe(STATE_TOPIC, (err) => {
            if (err) {
                console.error(`❌ Erro ao subscrever em ${STATE_TOPIC}:`, err);
            } else {
                console.log(`📡 Escutando tópico: ${STATE_TOPIC}`);
            }
        });

        // Subscribe em todos os tópicos Tuya para debug completo
        client.subscribe(ALL_TUYA_TOPIC, (err) => {
            if (err) {
                console.error(`❌ Erro ao subscrever em ${ALL_TUYA_TOPIC}:`, err);
            } else {
                console.log(`📡 Escutando todos os tópicos Tuya: ${ALL_TUYA_TOPIC}`);
            }
        });
        
        // Subscribe em tópicos alternativos possíveis
        const altTopics = [
            `tuya/${DEVICE_NAME}/status`,
            `tuya/${DEVICE_NAME}/response`,
            `tuya/${DEVICE_NAME}/#`,
            `tuya/+/state`,
            `tuya/+/status`
        ];
        
        altTopics.forEach(topic => {
            client.subscribe(topic, (err) => {
                if (!err) {
                    console.log(`📡 Escutando tópico alternativo: ${topic}`);
                }
            });
        });
    });

    client.on('error', (err) => {
        console.error('❌ Erro na conexão MQTT:', err);
    });

    client.on('message', (topic, message) => {
        const timestamp = new Date().toISOString();
        const messageStr = message.toString();
        
        // Verifica se é um eco do comando que enviamos
        const isEcho = lastSentPayload && 
                      topic === COMMAND_TOPIC && 
                      messageStr === JSON.stringify(lastSentPayload);
        
        if (isEcho) {
            console.log(`\n🔄 [${timestamp}] Echo do comando enviado (ignorando)`);
            console.log(`   Tópico: ${topic}`);
            console.log('─'.repeat(60));
            return;
        }
        
        // É uma mensagem de estado ou resposta do bridge
        console.log(`\n📥 [${timestamp}] Mensagem recebida:`);
        console.log(`   Tópico: ${topic}`);
        
        if (topic === STATE_TOPIC || topic.includes('/state')) {
            console.log(`   ✅ MENSAGEM DE ESTADO DETECTADA!`);
        } else if (topic === COMMAND_TOPIC) {
            console.log(`   ⚠️  Mensagem no tópico de comando (pode ser retransmissão do bridge)`);
        }
        
        try {
            const payload = JSON.parse(messageStr);
            console.log(`   Payload (formatado):`);
            console.log(JSON.stringify(payload, null, 2));
            
            // Analisa o payload para identificar campos importantes
            if (payload.dps) {
                console.log(`   📊 DPS detectados:`, Object.keys(payload.dps));
            }
            if (payload.work_mode || payload.bright_value_v2 || payload.colour_data_v2) {
                console.log(`   📊 Campos V2 detectados`);
            }
        } catch (e) {
            console.log(`   Payload (raw): ${messageStr}`);
        }
        console.log('─'.repeat(60));
    });

    client.on('close', () => {
        console.log('\n🔌 Conexão MQTT fechada');
    });
}

/**
 * Aguarda alguns segundos antes de enviar comando (para garantir conexão)
 */
function waitForConnection(callback, delay = 2000) {
    if (client && client.connected) {
        setTimeout(callback, delay);
    } else {
        console.log('⏳ Aguardando conexão...');
        setTimeout(() => waitForConnection(callback, delay), 500);
    }
}

/**
 * Publica mensagem no tópico de comando
 */
function publishCommand(payload, testName) {
    const jsonPayload = JSON.stringify(payload);
    lastSentPayload = payload;  // Guarda para filtrar ecos
    
    console.log(`\n📤 Enviando comando (${testName}):`);
    console.log(`   Tópico: ${COMMAND_TOPIC}`);
    console.log(`   Payload: ${jsonPayload}`);
    console.log(`   Payload (formatado):`);
    console.log(JSON.stringify(payload, null, 2));
    
    client.publish(COMMAND_TOPIC, jsonPayload, { qos: 1 }, (err) => {
        if (err) {
            console.error(`❌ Erro ao publicar:`, err);
        } else {
            console.log(`✅ Comando enviado com sucesso!`);
            console.log(`\n👀 Aguarde 5-10 segundos e observe:`);
            console.log(`   1. Se a lâmpada mudou de cor/brilho (confirmação visual)`);
            console.log(`   2. Mensagens de estado do bridge (se aparecerem)`);
            console.log(`\n💡 Dica: Se não aparecer mensagem de estado, o bridge pode não estar`);
            console.log(`   publicando estados ou o tópico pode ser diferente.\n`);
        }
    });
}

/**
 * TESTE A: Formato Abstraído
 * Formato que abstrai os DPS internos usando nomes amigáveis
 */
function testA_AbstractedFormat() {
    console.log('\n' + '='.repeat(60));
    console.log('🧪 TESTE A: Formato Abstraído');
    console.log('='.repeat(60));
    console.log('Descrição: Usa campos amigáveis como "brightness" e "color"');
    console.log('Esperado: O bridge tuya-mqtt converte para DPS internos\n');

    waitForConnection(() => {
        const payload = {
            set: true,  // Liga a lâmpada
            brightness: 500,  // Brilho médio (0-1000)
            color: {
                h: 120,   // Matiz (Hue): 0-360 (120 = verde)
                s: 1000,  // Saturação: 0-1000 (1000 = máximo)
                v: 1000   // Valor/Brilho: 0-1000 (1000 = máximo)
            }
        };
        
        publishCommand(payload, 'Teste A');
    });
}

/**
 * TESTE B: Formato DPS Numérico V3.3
 * Formato que usa IDs numéricos de DPS conforme protocolo Tuya 3.3
 * Baseado na documentação: DPS 21 = modo, DPS 22 = brilho, DPS 24 = cor
 */
function testB_DPSNumericV33() {
    console.log('\n' + '='.repeat(60));
    console.log('🧪 TESTE B: Formato DPS Numérico V3.3');
    console.log('='.repeat(60));
    console.log('Descrição: Usa IDs numéricos de DPS (21=mode, 22=brightness, 24=color)');
    console.log('Esperado: O bridge reconhece os DPS diretamente\n');

    waitForConnection(() => {
        // Abordagem 1: Envio direto com objeto dps
        const payload = {
            dps: {
                "21": "colour",  // Modo: "white" ou "colour"
                "22": 500,       // Brilho: 10-1000
                "24": JSON.stringify({  // Cor: JSON string embutida
                    h: 240,   // Azul
                    s: 1000,
                    v: 1000
                })
            }
        };
        
        publishCommand(payload, 'Teste B - Abordagem 1');
        
        // Aguarda 3 segundos e testa abordagem alternativa
        setTimeout(() => {
            console.log('\n🔄 Testando abordagem alternativa (multiple)...');
            const payload2 = {
                multiple: true,
                data: {
                    "21": "colour",
                    "22": 750,
                    "24": JSON.stringify({
                        h: 0,    // Vermelho
                        s: 1000,
                        v: 1000
                    })
                }
            };
            publishCommand(payload2, 'Teste B - Abordagem 2 (multiple)');
        }, 3000);
    });
}

/**
 * TESTE C: Formato DPS Nomeado
 * Formato que usa os nomes dos DPS conforme metadados extraídos (V2)
 */
function testC_DPSNamed() {
    console.log('\n' + '='.repeat(60));
    console.log('🧪 TESTE C: Formato DPS Nomeado');
    console.log('='.repeat(60));
    console.log('Descrição: Usa nomes dos DPS conforme metadados (work_mode, bright_value_v2, etc)');
    console.log('Esperado: O bridge reconhece os nomes dos campos V2\n');

    waitForConnection(() => {
        const payload = {
            set: {
                work_mode: "colour",      // Modo: "white" ou "colour"
                bright_value_v2: 500,     // Brilho: 10-1000
                colour_data_v2: JSON.stringify({  // Cor: JSON string
                    h: 300,   // Magenta
                    s: 1000,
                    v: 1000
                })
            }
        };
        
        publishCommand(payload, 'Teste C');
    });
}

/**
 * Modo apenas escuta (não envia comandos)
 */
function listenOnly() {
    console.log('\n' + '='.repeat(60));
    console.log('👂 MODO LISTEN: Apenas escutando mensagens');
    console.log('='.repeat(60));
    console.log('📡 Aguardando mensagens dos tópicos:');
    console.log(`   - ${STATE_TOPIC}`);
    console.log(`   - ${ALL_TUYA_TOPIC}`);
    console.log(`   - tuya/${DEVICE_NAME}/status`);
    console.log(`   - tuya/${DEVICE_NAME}/response`);
    console.log(`   - tuya/${DEVICE_NAME}/#`);
    console.log('\n💡 Instruções:');
    console.log('   1. Mude a cor/brilho pelo app oficial da Elgin/SmartLife');
    console.log('   2. Observe as mensagens que aparecem aqui');
    console.log('   3. Isso mostrará como a lâmpada reporta seu estado');
    console.log('\n⏹️  Pressione Ctrl+C para sair\n');
    
    isListening = true;
}

/**
 * Função principal
 */
function main() {
    const command = process.argv[2];

    if (!command) {
        console.log('\n📋 Uso do script:');
        console.log('   node tuya-debug.js <comando>\n');
        console.log('Comandos disponíveis:');
        console.log('   test-a    - Testa formato abstraído (brightness, color)');
        console.log('   test-b    - Testa formato DPS numérico V3.3');
        console.log('   test-c    - Testa formato DPS nomeado');
        console.log('   listen    - Apenas escuta mensagens (não envia comandos)\n');
        process.exit(1);
    }

    connectMQTT();

    // Aguarda conexão antes de executar comando
    setTimeout(() => {
        switch (command.toLowerCase()) {
            case 'test-a':
            case 'a':
                testA_AbstractedFormat();
                break;
            case 'test-b':
            case 'b':
                testB_DPSNumericV33();
                break;
            case 'test-c':
            case 'c':
                testC_DPSNamed();
                break;
            case 'listen':
            case 'l':
                listenOnly();
                break;
            default:
                console.error(`❌ Comando desconhecido: ${command}`);
                console.log('Use: test-a, test-b, test-c ou listen');
                process.exit(1);
        }
    }, 1500);

    // Mantém o script rodando
    process.on('SIGINT', () => {
        console.log('\n\n🛑 Encerrando script...');
        if (client) {
            client.end();
        }
        process.exit(0);
    });
}

main();
