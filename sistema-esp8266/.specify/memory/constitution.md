<!--
Sync Impact Report
- Version change: (none — initial ratification) → 1.0.0
- Modified principles:
  - [PRINCIPLE_1_NAME] → I. A borda observa e atua; o orquestrador decide
  - [PRINCIPLE_2_NAME] → II. Contratos de lâmpada e de ar que já funcionam
  - [PRINCIPLE_3_NAME] → III. Um aparelho lê a chave e comanda o ar
  - [PRINCIPLE_4_NAME] → IV. Autorização e segredos ficam no orquestrador
  - [PRINCIPLE_5_NAME] → V. Simplicidade e escopo explícito
- Added sections:
  - Stack e Contratos Estáveis
  - Fluxo de Desenvolvimento
  - Governança (conteúdo normativo)
- Removed sections: none (placeholders replaced in place)
- Follow-up TODOs: none
-->

# Automação Residencial Constitution

Este repositório é o sistema de automação da casa: painel, orquestrador,
broker MQTT, bridge das lâmpadas e o aparelho que comanda o ar-condicionado.
A constituição fixa o que já funciona e o que uma feature nova não pode
desfazer. A regra de uma feature vive na spec dela, não aqui.

## Core Principles

### I. A borda observa e atua; o orquestrador decide

O aparelho da casa publica fatos e executa um comando já decidido. A decisão
de negócio vive no orquestrador Spring Boot.

- O aparelho DEVE publicar o identificador lido, se há chave no suporte e a
  luminosidade medida naquele momento.
- O aparelho NÃO DECIDE se o identificador está registrado, se o ambiente
  conta como escuro, nem se a ordem é ligar, apagar ou alertar.
- Traduzir um comando já decidido para o protocolo do atuador PODE residir
  no aparelho. Para o ar, isso é receber potência e temperatura e emitir
  Coolix. Escolher temperatura, modo ou fazer o ar participar da chegada
  ou da saída da chave é decisão do orquestrador, e nesta linha de produto
  essa decisão está fora da regra de presença.

**Racional:** regra na borda e regra no Spring divergem no primeiro caso
não testado. O aparelho já provou o papel de adaptador do ar; a lista de
chaves e o limiar de luz são regra nova e ficam no orquestrador.

### II. Contratos de lâmpada e de ar que já funcionam

Feature nova usa os contratos abaixo. Outro protocolo de lâmpada ou de ar
exige emenda desta constituição.

- Lâmpadas Elgin A70 são alcançadas só pela bridge `tuya-mqtt`, na rede
  local. O orquestrador publica MQTT. A biblioteca `tuyapi` existe apenas
  dentro dessa bridge.
- Tópicos de comando: `tuya/lampada_1/command` e `tuya/lampada_2/command`.
  Consulta: sufixo `/query`. Estado: `tuya/+/state`.
- Data points que já foram medidos nestas lâmpadas:
  - DPS 20: energia, `true` ou `false`
  - DPS 21: modo, `"white"` ou `"colour"`, enviado antes da cor ou do brilho
  - DPS 22: brilho de 10 a 1000 (o pedido externo usa 0–100%)
  - DPS 24: cor HSV em hexadecimal `HHHHSSSSVVVV`
- Cor com diferença máxima entre R, G e B de até 30 conta como branco e
  usa o modo `"white"`.
- Ar-condicionado: tópico `/iot/sensores/ac/comando` com
  `{"power":"on"|"off","temp":17..30}`. Temperatura omitida vale 22.
  Ligado implica modo frio Coolix. Estado em `/iot/sensores/ac/estado`,
  incluindo o loopback do infravermelho.
- Telemetria de presença DEVE usar contrato próprio. Ela não reutiliza o
  tópico de comando do ar nem o tópico de comando das lâmpadas.

**Racional:** estes valores foram obtidos em hardware real. Um payload hex
cru no lugar do Coolix, ou `tuyapi` chamado pelo Spring, reabre um caminho
que já foi fechado.

### III. Um aparelho lê a chave e comanda o ar

O mesmo ESP8266 lê o RFID, lê o LDR e emite o infravermelho do ar. Não há
segundo aparelho nesta feature.

- O firmware DEVE continuar a aceitar o contrato Coolix do princípio II
  depois de passar a ler a chave e a luz.
- A espera depois do disparo infravermelho DEVE ser limitada. Uma espera
  sem teto, ou um `delay` que impeça o ciclo de notar uma chave, é
  proibida. A espera de 600 ms já usada para o loopback pode permanecer,
  desde que a leitura da chave retome ao fim dela.
- Wi-Fi e MQTT DEVEM reconectar sozinhos. O portal WiFiManager já existente
  permanece o modo de configurar a rede.
- Pinos podem ser refeitos. Emissão infravermelha e recepção de loopback
  DEVEM continuar presentes e verificáveis.

**Racional:** o MFRC522 e o infravermelho disputam pinos. A escolha foi
refazer a cablagem num firmware só, sem aposentar o ar que já funciona.

### IV. Autorização e segredos ficam no orquestrador

Quem pode mudar a casa é uma lista mantida pelo sistema, não uma constante
escondida no firmware.

- O aparelho PODE publicar todo identificador que conseguir ler.
- Só um identificador persistido na lista do orquestrador dispara a regra
  de morador. A lista DEVE sobreviver a reinício do servidor.
- Segredos de Tuya (`id`, `localKey`), senha MQTT, `JWT_SECRET` e senhas de
  banco NÃO PODEM entrar no código versionado nem na imagem. Entram por
  ambiente, no padrão já usado por `.env` e `application-dev.properties`
  de exemplo.
- Um log de presença PODE registrar que uma chave foi aceita ou recusada.
  NÃO PODE registrar segredo de lâmpada, senha ou token.

**Racional:** a saída da chave apaga as lâmpadas. Uma tag lida por engano
não pode herdar esse poder, e a lista tem de ser editável sem regravar o
ESP.

### V. Simplicidade e escopo explícito

A feature de presença acrescenta uma regra em cima dos contratos do
princípio II. Ela não troca o orquestrador.

- O orquestrador desta casa é o Spring Boot em `sistema-esp8266/app`. Um
  backend Node.js paralelo para decidir presença, lâmpada ou ar é proibido
  sem emenda.
- Alexa, `fauxmoESP` e qualquer descoberta de voz ficam fora até uma spec
  própria.
- Node-RED permanece fluxo legado e opcional. Regra nova não depende dele.
- O painel Next.js e o login já existentes são o lugar de uma ação do
  morador. Esta constituição não obriga uma tela nova; a spec da feature
  é que diz se o cadastro da chave aparece no painel.

**Racional:** o custo desta linha é perder o Coolix, a bridge e o painel
que já estão de pé. Complexidade extra entra por spec, não por atalho.

## Stack e Contratos Estáveis

| Camada | Escolha | Observação |
| --- | --- | --- |
| Borda | C++ / Arduino, um ESP8266 | MFRC522, LDR e `IRCoolixAC` (`IRremoteESP8266`) no mesmo firmware |
| Rede da borda | WiFiManager + MQTT | Reconexão automática. Broker endereçado por configuração, não por regra de negócio |
| Broker | Mosquitto | Único barramento entre borda, orquestrador e bridge |
| Orquestrador | Spring Boot (Java 17) em `sistema-esp8266/app` | JWT, MySQL, Flyway. Publica os contratos do princípio II |
| Lâmpadas | Bridge `tuya-mqtt` + `tuyapi` local | Perfil Compose `tuya`. Sem nuvem Tuya no caminho do comando |
| Painel | Next.js em `frontend/` | Consome a API do Spring. Não fala MQTT nem Tuya |
| Ar | Coolix via ESP8266 | Payload `power` + `temp` no tópico do princípio II |

Substituir uma linha desta tabela exige emenda constitucional.

## Fluxo de Desenvolvimento

- Toda feature começa por `spec.md`, segue para `plan.md` e só então para
  `tasks.md`, no Spec Kit deste diretório (`sistema-esp8266/.specify`).
- O plano marca o que já existe no repositório e limita o trabalho ao
  delta. Reimplementar lâmpada ou ar como prova de conceito viola o
  princípio II.
- Ao concluir uma fase do plano, ela é marcada como feita. A fase seguinte
  espera autorização de quem mantém o sistema.
- Regra de presença, lista de chaves e limiar de luz DEVEM ter teste
  automatizado no orquestrador, com o broker e as lâmpadas substituídos
  por dublês. O firmware demonstra o contrato num ensaio de bancada
  descrito no plano, porque o hardware não entra no teste de unidade.
- Um bug de regra corrigido DEVE chegar com o teste que falhava antes.

## Governance

Esta constituição prevalece sobre rascunhos, prompts e conveniência de
implementação. Spec e plano que a contradigam são defeito da spec, não
licença para o código.

- Versão segue semver. MAJOR: princípio removido ou redefinido de forma
  incompatível. MINOR: princípio ou seção nova. PATCH: redação que não
  muda a regra.
- Emenda atualiza `LAST_AMENDED_DATE`, o relatório de impacto no topo e a
  versão. A data de ratificação original não muda.
- Revisão de conformidade, em cada spec: a borda só publica fatos e executa
  comando decidido; lâmpadas e ar usam os contratos do princípio II; a
  lista de chaves não mora só no firmware; segredos ficam fora do Git.
- Exceção pontual exige frase explícita na spec ("Exceção à constituição:
  ...") e vale só para aquela feature.

**Version**: 1.0.0 | **Ratified**: 2026-10-21 | **Last Amended**: 2026-10-21
