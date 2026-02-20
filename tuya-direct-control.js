#!/usr/bin/env node

/**
 * Tuya Direct Control Script
 * 
 * Script que se conecta diretamente à lâmpada Tuya usando a biblioteca tuyapi,
 * sem precisar do bridge Docker.
 * 
 * Uso:
 *   node tuya-direct-control.js test-on        # Liga a lâmpada
 *   node tuya-direct-control.js test-off      # Desliga a lâmpada
 *   node tuya-direct-control.js test-color    # Muda cor para azul
 *   node tuya-direct-control.js test-bright   # Muda brilho para 50%
 *   node tuya-direct-control.js discover      # Descobre o IP da lâmpada
 */

const TuyAPI = require('tuyapi');

// Configuração da lâmpada (do devices.conf)
const DEVICE_CONFIG = {
    id: 'REMOVED_DEVICE_ID_1',
    key: 'REMOVED_DEVICE_KEY_1',
    ip: null,  // Será descoberto automaticamente se não especificado
    version: '3.3'
};

let device = null;

/**
 * Cria e conecta ao dispositivo Tuya
 */
async function connectDevice() {
    console.log('\n🔌 Conectando à lâmpada Tuya...');
    console.log(`   Device ID: ${DEVICE_CONFIG.id}`);
    console.log(`   IP: ${DEVICE_CONFIG.ip || 'auto-detect'}`);
    console.log(`   Version: ${DEVICE_CONFIG.version}\n`);

    const options = {
        id: DEVICE_CONFIG.id,
        key: DEVICE_CONFIG.key,
        version: DEVICE_CONFIG.version
    };

    if (DEVICE_CONFIG.ip) {
        options.ip = DEVICE_CONFIG.ip;
    }

    device = new TuyAPI(options);

    device.on('connected', () => {
        console.log('✅ Conectado à lâmpada!\n');
    });

    device.on('disconnected', () => {
        console.log('❌ Desconectado da lâmpada\n');
    });

    device.on('error', (error) => {
        console.error('❌ Erro:', error.message);
    });

    device.on('data', (data) => {
        console.log('📥 Dados recebidos da lâmpada:');
        console.log(JSON.stringify(data, null, 2));
        console.log('');
    });

    try {
        await device.find();
        await device.connect();
        return true;
    } catch (error) {
        console.error('❌ Erro ao conectar:', error.message);
        if (error.message.includes('timeout') || error.message.includes('ECONNREFUSED')) {
            console.log('\n💡 Dica: Certifique-se de que:');
            console.log('   1. A lâmpada está ligada e na mesma rede Wi-Fi');
            console.log('   2. O IP da lâmpada está correto (ou deixe vazio para auto-detectar)');
            console.log('   3. O device ID e key estão corretos\n');
        }
        return false;
    }
}

/**
 * Obtém o estado atual da lâmpada
 */
async function getState() {
    try {
        const state = await device.get();
        console.log('📊 Estado atual da lâmpada:');
        console.log(JSON.stringify(state, null, 2));
        return state;
    } catch (error) {
        console.error('❌ Erro ao obter estado:', error.message);
        return null;
    }
}

/**
 * Converte HSV para formato hexadecimal Tuya
 * Formato: HHSSVV (Hue 0-360, Saturation 0-1000, Value 0-1000)
 */
function hsvToTuyaHex(h, s, v) {
    // Normaliza valores
    const hue = Math.round(h); // 0-360
    const sat = Math.round(s); // 0-1000
    const val = Math.round(v); // 0-1000
    
    // Converte para hexadecimal (2 bytes cada)
    const hHex = hue.toString(16).padStart(4, '0');
    const sHex = sat.toString(16).padStart(4, '0');
    const vHex = val.toString(16).padStart(4, '0');
    
    return hHex + sHex + vHex;
}

/**
 * Teste 1: Liga a lâmpada
 */
async function testOn() {
    console.log('\n' + '='.repeat(60));
    console.log('🧪 TESTE: Ligar Lâmpada');
    console.log('='.repeat(60) + '\n');

    if (!await connectDevice()) {
        return;
    }

    try {
        // DPS 20 é o power (descoberto nos logs)
        await device.set({ dps: 20, set: true });
        console.log('✅ Comando de ligar enviado!\n');
        
        // Aguarda um pouco e verifica o estado
        await new Promise(resolve => setTimeout(resolve, 2000));
        await getState();
    } catch (error) {
        console.error('❌ Erro ao enviar comando:', error.message);
    } finally {
        device.disconnect();
    }
}

/**
 * Teste 2: Desliga a lâmpada
 */
async function testOff() {
    console.log('\n' + '='.repeat(60));
    console.log('🧪 TESTE: Desligar Lâmpada');
    console.log('='.repeat(60) + '\n');

    if (!await connectDevice()) {
        return;
    }

    try {
        // DPS 20 é o power (descoberto nos logs)
        await device.set({ dps: 20, set: false });
        console.log('✅ Comando de desligar enviado!\n');
        
        await new Promise(resolve => setTimeout(resolve, 2000));
        await getState();
    } catch (error) {
        console.error('❌ Erro ao enviar comando:', error.message);
    } finally {
        device.disconnect();
    }
}

/**
 * Teste 3: Muda cor para azul
 */
async function testColor() {
    console.log('\n' + '='.repeat(60));
    console.log('🧪 TESTE: Mudar Cor para Azul');
    console.log('='.repeat(60) + '\n');

    if (!await connectDevice()) {
        return;
    }

    try {
        // Primeiro obtém o estado para ver os DPS disponíveis
        const state = await getState();
        
        console.log('\n📤 Enviando comando de cor...\n');
        
        // DPS 21 = modo, DPS 22 = brilho, DPS 24 = cor (hexadecimal)
        // Azul: Hue=240, Saturation=1000, Value=1000
        const colorHex = hsvToTuyaHex(240, 1000, 1000);
        console.log(`   Cor hexadecimal gerada: ${colorHex}\n`);
        
        // Abordagem: Enviar cor primeiro, depois modo (pode ser que a ordem importe)
        // Primeiro: muda cor e brilho
        await device.set({
            multiple: true,
            data: {
                '22': 500,       // Brilho médio
                '24': colorHex   // Cor azul em hexadecimal
            }
        });
        
        console.log('   ✅ Cor e brilho enviados');
        
        // Aguarda um pouco
        await new Promise(resolve => setTimeout(resolve, 500));
        
        // Depois: força modo colour
        await device.set({
            multiple: true,
            data: {
                '21': 'colour'   // Modo cor (enviado depois para garantir)
            }
        });
        
        console.log('   ✅ Modo colour enviado\n');
        
        await new Promise(resolve => setTimeout(resolve, 2000));
        await getState();
        
        console.log('\n💡 IMPORTANTE: A lâmpada mudou de cor visualmente? (azul)');
    } catch (error) {
        console.error('❌ Erro ao enviar comando:', error.message);
        console.error('Stack:', error.stack);
    } finally {
        device.disconnect();
    }
}

/**
 * Teste 4: Muda brilho (mantendo modo colour se já estiver)
 */
async function testBrightness() {
    console.log('\n' + '='.repeat(60));
    console.log('🧪 TESTE: Mudar Brilho para 50%');
    console.log('='.repeat(60) + '\n');

    if (!await connectDevice()) {
        return;
    }

    try {
        // Obtém estado atual para ver o modo
        const state = await getState();
        const currentMode = state?.dps?.['21'];
        
        console.log(`   Modo atual: ${currentMode}\n`);
        
        // DPS 22 é o brilho (descoberto nos logs)
        const payload = { dps: 22, set: 500 }; // Brilho 50% (500 de 1000)
        
        // Se estiver em modo colour, mantém o modo
        if (currentMode === 'colour') {
            console.log('   Mantendo modo colour...\n');
            await device.set({
                multiple: true,
                data: {
                    '22': 500,
                    '21': 'colour'  // Mantém modo colour
                }
            });
        } else {
            await device.set(payload);
        }
        
        console.log('✅ Comando de brilho enviado!\n');
        
        await new Promise(resolve => setTimeout(resolve, 2000));
        await getState();
    } catch (error) {
        console.error('❌ Erro ao enviar comando:', error.message);
    } finally {
        device.disconnect();
    }
}

/**
 * Descobre o IP da lâmpada
 */
async function discover() {
    console.log('\n' + '='.repeat(60));
    console.log('🔍 DESCOBRINDO IP DA LÂMPADA');
    console.log('='.repeat(60) + '\n');
    console.log('Aguardando resposta da lâmpada...\n');

    const device = new TuyAPI({
        id: DEVICE_CONFIG.id,
        key: DEVICE_CONFIG.key,
        version: DEVICE_CONFIG.version
    });

    try {
        const ip = await device.find();
        console.log(`✅ IP da lâmpada encontrado: ${ip}\n`);
        console.log('💡 Adicione este IP no arquivo tuya-mqtt/devices.conf');
        console.log(`   ip = ${ip}\n`);
        return ip;
    } catch (error) {
        console.error('❌ Erro ao descobrir IP:', error.message);
        console.log('\n💡 Certifique-se de que:');
        console.log('   1. A lâmpada está ligada');
        console.log('   2. A lâmpada está na mesma rede Wi-Fi');
        console.log('   3. O device ID está correto\n');
        return null;
    }
}

/**
 * Função principal
 */
async function main() {
    const command = process.argv[2];

    if (!command) {
        console.log('\n📋 Uso do script:');
        console.log('   node tuya-direct-control.js <comando>\n');
        console.log('Comandos disponíveis:');
        console.log('   discover    - Descobre o IP da lâmpada');
        console.log('   test-on     - Liga a lâmpada');
        console.log('   test-off    - Desliga a lâmpada');
        console.log('   test-color  - Muda cor para azul');
        console.log('   test-bright - Muda brilho para 50%\n');
        process.exit(1);
    }

    switch (command.toLowerCase()) {
        case 'discover':
            await discover();
            break;
        case 'test-on':
        case 'on':
            await testOn();
            break;
        case 'test-off':
        case 'off':
            await testOff();
            break;
        case 'test-color':
        case 'color':
            await testColor();
            break;
        case 'test-bright':
        case 'bright':
            await testBrightness();
            break;
        default:
            console.error(`❌ Comando desconhecido: ${command}`);
            console.log('Use: discover, test-on, test-off, test-color ou test-bright');
            process.exit(1);
    }
}

main().catch(console.error);
