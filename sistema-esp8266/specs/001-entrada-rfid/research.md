# Research: Entrada por chave RFID

Decisões do delta. Coolix e a bridge Tuya não foram reabertos.

## 1. Onde a regra mora

- **Decision**: A regra de presença é um componente novo no Spring Boot em `sistema-esp8266/app`. O firmware só publica fatos e continua executando Coolix quando o tópico do ar recebe comando.
- **Rationale**: A constituição põe registrado versus desconhecido, escuro versus claro e a ordem às lâmpadas no orquestrador. O sketch já provou o papel de adaptador do ar.
- **Alternatives considered**: Decidir no ESP8266 e só então publicar “ligar” ou “vermelho”. Isso duplica a lista e o limiar na borda e foi rejeitado pelo princípio I. Um backend Node paralelo foi rejeitado pelo princípio V.

## 2. Contrato de telemetria

- **Decision**: Tópico novo `/iot/entrada/presenca`. QoS 0, sem retain. Publicar na mudança e repetir o último fato a cada 2 s. Campos: `present` (boolean), `uid` (hex, só quando há chave lida), `lux` (inteiro 0–1023, omitido quando o ADC não foi amostrado).
- **Rationale**: A constituição exige contrato próprio, separado de `/iot/sensores/ac/comando` e de `tuya/lampada_*/command`. Sem retain, um ESP desligado não deixa um “vazio” antigo apagar a casa quando o Spring reinicia. O heartbeat de 2 s cabe nos 3 s de SC-001 mesmo se a mensagem de mudança se perder. QoS 0 chega para um fato que se repete.
- **Alternatives considered**: Reusar o tópico de estado do ar. Mistura presença com loopback Coolix. Retain=true. Um fato velho com `present: false` apagaria as lâmpadas com o ESP fora do ar.

## 3. Quem conta os 5 segundos

- **Decision**: O Spring arma um timer de 5 s no primeiro `present: false` vindo de uma leitura concluída sem cartão. Outro `present: false` igual, incluindo o aviso a cada 2 s, não reinicia esse prazo. Qualquer `present: true` antes dos 5 s cancela o timer. Heartbeat com o mesmo `uid` não manda ordem nova às lâmpadas. Troca de `uid` sem `present: false` no meio aplica a regra nova na hora.
- **Rationale**: FR-009 e o caso de troca direta já estão fechados na spec. Repetir a ordem a cada heartbeat brigaria com o painel enquanto a chave está parada no suporte. Reiniciar os 5 s a cada `present: false` também falha: o aviso de 2 s nunca deixaria o prazo completar.
- **Alternatives considered**: Tratar buraco de MQTT como ausência. Um blip do broker apagaria a casa. Contar os 5 s no firmware. A decisão de negócio sairia da borda.

## 4. Leitura ilegível não é suporte vazio

- **Decision**: `present: false` sai só quando o MFRC522 termina a varredura e informa que não há cartão. Erro de comunicação do leitor não publica ausência e não muda o último fato.
- **Rationale**: A spec diz que chave ilegível não muda lâmpadas nem ar. Publicar vazio nesse erro dispararia o apagar aos 5 s.
- **Alternatives considered**: Qualquer falha de `PICC_ReadCardSerial` vira ausência. Contradiz o caso de chave ilegível.

## 5. Limiar de luz

- **Decision**: O firmware manda o valor cru de `analogRead(A0)`. O Spring guarda um inteiro anulável. Escuro significa `lux` presente e limiar definido e `lux < limiar`. Limiar nulo, ou `lux` omitido, trata a chegada de morador como claro. O número inicial não vem de fábrica; a calibração no suporte grava o limiar pela API admin, e ele sobrevive a reinício. O LDR só liga a A0 por um divisor que mantém o pino em no máximo cerca de 1 V. O limiar só se grava depois de um valor baixo no escuro e um valor alto no claro.
- **Rationale**: FR-016 e FR-017. O ADC do ESP8266 é o único pino analógico, aceita cerca de 1 V e não disputa o SPI. Sem divisor, 3,3 V estraga a entrada ou satura a leitura, e “escuro” nunca acontece. O ensaio confirma que valor maior é mais luz antes de gravar o limiar. A comparação fica num único ponto do Spring.
- **Alternatives considered**: Limiar compilado no firmware. Exige regravar o aparelho para calibrar. Um default numérico chutado. A spec deixa o número para o local.

## 6. Lista de chaves

- **Decision**: Tabela no MySQL, UID em hexadecimal maiúsculo sem separadores, comprimento de 8 a 20 caracteres. Inclusão e remoção em `/api/admin/entrada/chaves`, que já exige `ROLE_ADMIN` pela regra atual de `/api/admin/**`. A lista vale na assentada seguinte, sem regravar o ESP, e permanece depois do reinício.
- **Rationale**: User Story 4 e princípio IV. O administrador já existe (`ROLE_ADMIN`). Não há perfil novo.
- **Alternatives considered**: Lista num arquivo no ESP ou constante no sketch. Não sobrevive como dado editável e esconde autorização na borda. Endpoint só autenticado, sem papel admin. A spec reserva o cadastro a quem já administra a casa.

## 7. Cor lembrada e alerta

- **Decision**: O Spring lembra, em linha durável, a última cor e o brilho de uso normal, mais um marcador de que um alerta terminou com as lâmpadas apagadas. Vermelho de alerta (`#FF0000`, brilho 100) não entra nessa memória. Branco de fallback é `#FFFFFF` no brilho 100, no formato que `TuyaLightingService` já converte. Comando manual bem-sucedido pelo caminho já existente grava a cor normal e limpa o marcador. A regra de presença não altera o codificador de DPS.
- **Rationale**: FR-006 distingue a cor do morador do vermelho. O caso “alerta terminou apagado, próxima chegada no escuro é branco no máximo” precisa sobreviver a um reinício do Spring, senão a cor antiga voltaria.
- **Alternatives considered**: Ler a cor só do estado ao vivo da lâmpada e não guardar nada. Com as lâmpadas apagadas depois do alerta, o estado ao vivo não diz que a próxima chegada tem de ser branco.

## 8. Efeito nas duas lâmpadas

- **Decision**: Cada transição emite no máximo uma ordem igual para `lampada_1` e `lampada_2`, pelo `TuyaLightingService` já existente. Antes de decidir “já acesas numa cor normal” ou “vermelhas”, o Spring lê o estado atual das duas em `tuya/+/state` ou por consulta. A ordem só sai depois dessa leitura. Se a leitura falhar, a chegada no escuro ainda acende. Se uma chamada de comando falhar, a outra ainda é feita. Se as duas falharem, a transição fica sem sucesso e a próxima transição de presença envia de novo. Heartbeat do mesmo uid não reenvia.
- **Rationale**: FR-015 e FR-018. Sem o estado ao vivo, a regra não distingue cor do morador de alerta vermelho. Reusar o serviço preserva DPS 20/21/22/24 e a ordem modo-antes-da-cor.
- **Alternatives considered**: Publicar um payload novo direto no tópico Tuya, fora do serviço. Reabre o protocolo que a constituição fechou.

## 9. Pinos

- **Decision**: IR de envio permanece em GPIO 5 (D1). IR de recepção sai de GPIO 12, que é MISO do SPI, e vai para GPIO 4 (D2). MFRC522 usa o SPI de hardware: CLK GPIO 14 (D5), MISO GPIO 12 (D6), MOSI GPIO 13 (D7), SS GPIO 2 (D4), RST GPIO 16 (D0). LDR em A0, atrás do divisor de 1 V. O LED RGB de status sai do sketch. Não há pino livre para o manter, e estes pinos não voltam a ser LED. O portal WiFiManager continua. O estado do aparelho é o Monitor Serial a 115200. SS e RST podem ser trocados na bancada se o boot exigir, sem mexer no IR de envio nem no loopback.
- **Rationale**: O sketch de hoje usa GPIO 12, 13 e 14 para IR de recepção e LEDs, e esses três pinos são o SPI de hardware. GPIO 4, que era o LED vermelho, passa a ser o receptor. O princípio III manda preservar envio e loopback e permite refazer o resto. GPIO 2 em idle alto combina com SS ativo em baixo e com o strap de boot. GPIO 16 serve como reset do leitor, que não precisa de interrupção. Manter o RGB no mesmo sítio parte o SPI ou o loopback.
- **Alternatives considered**: Segundo ESP só para o RFID. A spec e a constituição fecharam um aparelho só. SPI por software em outros pinos, mantendo o LED RGB. Gasta CPU no ciclo da chave e complica o loopback; fica como reserva se a bancada mostrar que o SPI de hardware não cabe.

## 10. MQTT do firmware sem IP fixo

- **Decision**: O host, a porta, o usuário e a senha do broker passam a ser parâmetros do WiFiManager, gravados na flash do ESP (LittleFS ou o armazenamento de parâmetros do portal), lidos na subida. Não há literal `192.168.1.2` nem senha no sketch. Porta vazia com host preenchido usa 1883. Host vazio não conecta num endereço de casa embutido; o portal é o caminho para gravar o broker. Reconexão Wi-Fi e MQTT que o sketch já faz permanece.
- **Rationale**: A constituição proíbe segredo versionado e pede o broker por configuração. O sketch atual tem `MQTT_SERVER = "192.168.1.2"` e `client.connect` sem usuário. O delta troca isso sem aposentar o WiFiManager.
- **Alternatives considered**: Manter o IP e só documentar. Continua um endereço de uma casa dentro do Git, sem caminho de mudança. Arquivo de credencial commitado ao lado do sketch. É segredo versionado.

## 11. Espera de 600 ms

- **Decision**: `IR_PROCESSING_DELAY_MS` continua 600. O loopback ainda é avaliado ao fim dessa janela. O ciclo deixa de ficar parado dentro de um `delay` que impeça a chave: a espera vira um prazo, `client.loop()` e a varredura do MFRC522 seguem, e o loopback corre quando os 600 ms completam. Nada alonga essa janela.
- **Rationale**: A constituição permite a espera de 600 ms desde que a leitura da chave retome ao fim, e proíbe espera sem teto. Um prazo mantém o teto e ainda deixa a chave ser vista durante o envio do ar, o que cabe em FR-014. 600 ms é menor que os 5 s de ausência, então um único disparo não completa a saída.
- **Alternatives considered**: `delay(600)` bloqueante como hoje. É legal se a chave rodar logo em seguida, mas durante a espera o MQTT e o leitor param. Alongar a espera para “dar tempo” ao RFID. Atravessa o teto.

## 12. O que não entra nesta leva

- **Decision**: Nenhuma tela nova. O serviço guarda em memória o último fato (vazio ou uid, lux, decisão) para uma consulta futura do painel. Essa rota e a página ficam fora das tarefas. Alexa, fauxmoESP e Node-RED não participam.
- **Rationale**: A spec deixa a forma da tela para depois, e o pedido desta leva tira o painel do escopo de implementação. O último fato em memória evita desenhar a UI agora e ainda deixa o gancho.
- **Alternatives considered**: Projetar a página do suporte junto com a regra. Aumenta o escopo e mexe em `frontend/` nesta leva.
