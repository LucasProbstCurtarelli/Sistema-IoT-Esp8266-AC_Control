/**
 * Tuya MQTT Bridge Configuration
 * 
 * Copy this file to tuya-config.js and fill in your device credentials.
 * IMPORTANT: Do not commit tuya-config.js to version control!
 */

module.exports = {
    // MQTT Broker URL
    MQTT_BROKER: 'mqtt://localhost:1883',
    
    // Command debounce delay in milliseconds
    COMMAND_DEBOUNCE_MS: 200,
    
    // Tuya Device Configuration
    // Add your devices here with their actual credentials
    DEVICES: {
        lampada_1: {
            id: 'YOUR_DEVICE_ID_HERE',
            key: 'YOUR_DEVICE_KEY_HERE',
            version: '3.3',
            // Optional: specify IP to skip discovery
            // ip: '192.168.1.100',
        },
        lampada_2: {
            id: 'YOUR_DEVICE_ID_HERE',
            key: 'YOUR_DEVICE_KEY_HERE',
            version: '3.3',
        },
        // Add more devices as needed:
        // lampada_3: {
        //     id: 'YOUR_DEVICE_ID_HERE',
        //     key: 'YOUR_DEVICE_KEY_HERE',
        //     version: '3.3',
        // },
    },
};
