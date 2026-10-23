# Implementation Plan: Entrada por chave RFID

**Branch**: `001-entrada-rfid` | **Date**: 2026-10-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-entrada-rfid/spec.md`

## Summary

O mesmo ESP8266 que já envia Coolix passa a ler o MFRC522 e o LDR e a publicar fatos. O Spring Boot que já comanda as lâmpadas decide se a chave é registrada, se o ambiente conta como escuro, e manda o mesmo efeito para `lampada_1` e `lampada_2`. O ar-condicionado fica fora dessa regra.

Coolix e a bridge Tuya (DPS 20/21/22/24, tópicos `tuya/lampada_1/command` e `tuya/lampada_2/command`) já estão feitos. Este plano só cobre o delta: pinos, RFID, LDR, telemetria própria, lista de chaves, limiar e a regra de presença. O painel mostrará o estado do suporte numa leva seguinte; esta leva não desenha essa tela.

## Status de implementação

Já feito, fora do delta:

- [x] Ar-condicionado Coolix no firmware atual (`firmware/ProjetoAutomacaoResidencialLucas.ino`): tópico `/iot/sensores/ac/comando`, payload `{power, temp}`, `IRCoolixAC`, espera de 600 ms e loopback em `/iot/sensores/ac/estado`.
- [x] WiFiManager já é o portal de rede desse firmware.
- [x] Lâmpadas Elgin pela bridge `tuya-mqtt`: DPS 20, 21, 22 e 24; comando em `tuya/lampada_1/command` e `tuya/lampada_2/command`; consulta `/query`; estado `tuya/+/state`. O Spring já publica isso em `TuyaLightingService`. O comportamento desses DPS não muda.

Feito neste delta:

- [x] Refação de pinos do ESP8266, com emissão infravermelha e loopback preservados.
- [x] Leitura MFRC522 no mesmo firmware. A espera de infravermelho continua limitada a 600 ms e, ao fim dela, o ciclo da chave volta a rodar.
- [x] Leitura do LDR no mesmo firmware, com divisor que mantém o pino A0 em no máximo cerca de 1 V. O limiar só é gravado depois de um valor baixo no escuro e um valor alto no claro.
- [x] Fim do LED RGB de estado. D2, D5, D6 e D7 passam ao infravermelho de recepção e ao SPI. O estado do aparelho fica no Monitor Serial, a 115200.
- [x] Endereço e credenciais MQTT do firmware vindos de configuração. O `192.168.1.2` fixo do sketch deixa de ser o endereço do broker.
- [x] Telemetria de presença no contrato MQTT próprio (`/iot/entrada/presenca`), sem reutilizar o tópico do ar nem os tópicos de comando das lâmpadas.
- [x] Regra de presença no Spring: 5 s de suporte vazio, registrada versus desconhecida, escuro versus claro, as duas lâmpadas juntas, ar fora da regra. O primeiro `present: false` arma os 5 s. Repetições do mesmo vazio, incluindo o aviso a cada 2 s, não reiniciam o prazo. Só `present: true` o cancela.
- [x] Antes de decidir “já estão acesas” ou “estão vermelhas”, o Spring lê o estado atual das duas lâmpadas em `tuya/+/state` ou por consulta. A ordem só sai depois dessa leitura. Se a leitura falhar, a chegada no escuro ainda acende.
- [x] Lista durável de identificadores e limiar de luz configurável, ambos sobrevivendo a reinício.
- [x] Testes do orquestrador com broker e lâmpadas substituídos por dublês.

O painel Next.js em `frontend/` mostrará depois o estado do suporte. Não há tarefa de tela nesta leva.

## Technical Context

**Language/Version**: Firmware C++/Arduino (ESP8266, `IRremoteESP8266`). Orquestrador Java 17, Spring Boot 3.5 em `sistema-esp8266/app`.

**Primary Dependencies**: WiFiManager, PubSubClient, MFRC522, LDR no ADC. No Spring: o cliente MQTT Eclipse Paho já usado, Flyway, JPA, MySQL, a bridge `tuya-mqtt` já em execução. `tuyapi` permanece só na bridge.

**Storage**: MySQL via Flyway. A lista de chaves, o limiar e a memória da última cor normal moram no orquestrador. A migração desta feature usa a próxima versão livre depois de `V5__create_light_link_map`.

**Testing**: JUnit 5 no orquestrador, com dublês no lugar do broker e das lâmpadas (`./mvnw.cmd test` em `sistema-esp8266/app`). O firmware se demonstra em bancada, descrita em [quickstart.md](./quickstart.md). O hardware não entra no teste de unidade.

**Target Platform**: Um ESP8266 na entrada; Spring Boot na rede local; Mosquitto como único barramento; duas lâmpadas Elgin A70 já instaladas.

**Project Type**: Firmware de borda mais serviço web existente. O painel Next.js só consome a API mais tarde.

**Performance Goals**: Lâmpadas no estado certo em até 3 s depois da chave assentar (SC-001, SC-003) e em até 3 s depois de completados os 5 s de suporte vazio (SC-002).

**Constraints**: Um aparelho só. Espera de infravermelho no máximo 600 ms, e o ciclo da chave retoma ao fim. Broker e senha MQTT configuráveis, sem IP de casa gravado no sketch. Segredos fora do Git. A regra não publica em `/iot/sensores/ac/comando` nem em `tuya/lampada_*/command` a partir do firmware. O LDR entra em A0 só através de um divisor que limita o pino a cerca de 1 V. Não há LED RGB de estado neste aparelho. O prazo de 5 s não reinicia quando o mesmo `present: false` se repete. A cor atual das lâmpadas vem de `tuya/+/state` ou de uma consulta, antes da ordem.

**Scale/Scope**: Uma casa, um suporte, uma chave por vez, duas lâmpadas, um administrador já autenticado.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Resultado | Como o plano cumpre |
| --- | --- | --- |
| I. A borda observa e atua; o orquestrador decide | Passa | O firmware publica `present`, `uid` e `lux`. Registrado, escuro e a ordem às lâmpadas são decisão do Spring. |
| II. Contratos de lâmpada e de ar que já funcionam | Passa | Coolix e os DPS 20/21/22/24 ficam como estão. Presença usa `/iot/entrada/presenca`. O Spring fala com as lâmpadas pelo serviço que já existe. `tuyapi` não entra no Spring. |
| III. Um aparelho lê a chave e comanda o ar | Passa | O mesmo sketch ganha MFRC522 e LDR. IR de envio e loopback continuam. A espera de 600 ms não cresce e o ciclo da chave retoma. WiFiManager permanece. Pinos podem mudar. |
| IV. Autorização e segredos ficam no orquestrador | Passa | A lista é tabela no MySQL, editável pela API admin, sem regravar o ESP. MQTT, JWT e banco continuam por ambiente. O sketch não leva senha nem o IP fixo. Log de presença registra aceita ou recusada, sem segredo de lâmpada, senha ou token. |
| V. Simplicidade e escopo explícito | Passa | A regra entra no Spring Boot em `sistema-esp8266/app`. Sem backend Node paralelo, sem Alexa, sem depender de Node-RED. Sem tela nova nesta leva. |

Reavaliação depois do desenho (contratos, modelo e quickstart): os cinco princípios continuam passando. Nenhuma exceção à constituição.

## Project Structure

### Documentation (this feature)

```text
sistema-esp8266/specs/001-entrada-rfid/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── presenca-mqtt.md
│   ├── entrada-admin.md
│   └── lampadas-consumidas.md
├── checklists/
│   └── requirements.md
└── tasks.md             # não criado por este plano
```

### Source Code (repository root)

```text
firmware/ProjetoAutomacaoResidencialLucas.ino
    Coolix, WiFiManager e loopback já existem.
    Delta: pinos, MFRC522, LDR, MQTT configurável, publicação em /iot/entrada/presenca.

sistema-esp8266/app/src/main/java/com/iot/app/
├── controller/          # LightingController e AcController prontos; novo controller admin da entrada
├── service/             # TuyaLightingService pronto; novo assinante MQTT e a regra de presença
├── model/               # nova chave autorizada e a linha de ajuste (limiar e cor lembrada)
├── repository/
└── config/MqttConfig.java

sistema-esp8266/app/src/main/resources/db/migration/
    próxima versão depois de V5__create_light_link_map.sql

sistema-esp8266/app/src/test/java/com/iot/app/
    testes da regra com dublês

frontend/                # fora desta leva; depois mostra o estado do suporte
```

**Structure Decision**: Monorepo já existente. A feature altera o sketch único em `firmware/` e acrescenta pacotes dentro de `com.iot.app`. Não cria serviço novo nem segundo microcontrolador. `tasks.md` fica para o comando de tarefas.

## Complexity Tracking

Nenhuma violação da constituição. Não há exceção a registrar.

## Phase 0 e Phase 1

Pesquisa fechada em [research.md](./research.md). Modelo em [data-model.md](./data-model.md). Contratos em [contracts/](./contracts/). Roteiro de ensaio em [quickstart.md](./quickstart.md).

Decisões que o implementador não reabre, porque a spec já as fechou:

- Suporte vazio por 5 s contínuos apaga as duas lâmpadas. Uma falha mais curta, com a mesma chave de volta, não apaga. O primeiro `present: false` arma o prazo. Cada repetição do vazio, inclusive o aviso a cada 2 s, não o reinicia. Só `present: true` cancela.
- “Já acesas” e “vermelhas” leem-se em `tuya/+/state` ou numa consulta às duas lâmpadas, antes de qualquer ordem desta regra. Leitura falhada não impede acender no escuro.
- O LED RGB de estado sai. D2 passa a ser o receptor de infravermelho. D5, D6 e D7 são o SPI do MFRC522. Quem opera o aparelho olha o Monitor Serial a 115200.
- O LDR usa um divisor. A0 não recebe mais do que cerca de 1 V. O limiar só se grava depois de um `lux` baixo no escuro e um `lux` alto no claro.
- Chave desconhecida deixa as duas lâmpadas vermelhas no brilho máximo, de dia ou de noite. Ao sair, as duas apagam, qualquer que fosse o estado anterior.
- Chave registrada no escuro acende as duas, ou deixa-as como estão se já estavam acesas fora do alerta. No claro, essa chegada não muda as lâmpadas.
- Troca direta, sem intervalo vazio, aplica na hora a regra da chave nova.
- O ar-condicionado não liga, desliga nem muda de temperatura por causa da chave. Um comando de ar pelo controle já existente continua válido com a chave no suporte.
- Luminosidade ausente conta como claro na chegada de morador. O alerta vermelho não depende da luz.
- As duas lâmpadas recebem a mesma ordem. Se uma falhar, a outra ainda é comandada. Se as duas falharem, a casa não informa sucesso e a transição seguinte envia de novo.
