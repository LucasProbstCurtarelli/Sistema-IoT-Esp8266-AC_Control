FROM node:20-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev
COPY tuya-mqtt-bridge.js ./
USER node
CMD ["node", "tuya-mqtt-bridge.js"]
