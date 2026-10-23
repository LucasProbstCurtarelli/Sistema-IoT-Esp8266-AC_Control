# Quickstart: validar a entrada RFID

Ensaio da feature depois de implementada. Não substitui `tasks.md`. Os cenários abaixo provam a spec; o detalhe de código fica na implementação.

Coolix e as lâmpadas Tuya já funcionam. O ensaio não os reimplementa. Confere que continuam de pé enquanto a chave é exercitada.

## Pré-requisitos

- Broker Mosquitto da casa já acessível. Não subir a pilha inteira pela raiz do repositório (`npm start` sobe Node-RED, MQTT e a bridge juntos).
- Spring Boot de `sistema-esp8266/app` apontando para esse broker por `MQTT_BROKER_URL`, `MQTT_USERNAME` e `MQTT_PASSWORD`.
- Bridge `tuya-mqtt` já responsável por `lampada_1` e `lampada_2`.
- ESP8266 com o sketch único, IR de envio em D1, IR de loopback em D2, MFRC522 no SPI (D5, D6, D7, SS em D4, RST em D0) e LDR em A0 atrás de um divisor que mantém A0 em no máximo cerca de 1 V. Não há LED RGB. O estado lê-se no Monitor Serial a 115200. Broker gravado pelo portal WiFiManager, sem IP fixo no código.
- Um UID incluído em `POST /api/admin/entrada/chaves` e um segundo cartão que não está na lista.
- Limiar gravado em `PUT /api/admin/entrada/limiar` só depois de ver um `lux` baixo no escuro e um `lux` alto no claro. Enquanto `threshold` for `null`, chegada de morador não acende as lâmpadas.

## Teste automatizado do orquestrador

No diretório `sistema-esp8266/app`:

```text
.\mvnw.cmd test
```

O teste da regra usa dublês no lugar do broker e das lâmpadas. Tem de cobrir, sem hardware:

- Registrada e `lux` abaixo do limiar pede as duas lâmpadas acesas na cor lembrada, ou branco no brilho máximo se não houver cor ou se o alerta anterior terminou apagado.
- Registrada e `lux` igual ou acima do limiar, ou `lux` ausente, ou limiar nulo, não pede mudança na chegada a partir do vazio.
- Desconhecida pede vermelho no brilho máximo nas duas, com ou sem `lux`.
- `present: false` por menos de 5 s, seguido do mesmo UID, não pede apagar.
- Vários `present: false` espaçados de 2 s não alongam os 5 s. O apagar sai 5 s depois do primeiro vazio.
- `present: false` por 5 s pede apagar as duas.
- A decisão “já acesas” ou “vermelhas” usa o estado lido das lâmpadas antes da ordem.
- Troca de UID sem `present: false` aplica a regra nova sem esperar 5 s, inclusive desconhecida para registrada no escuro (branco no máximo) e no claro (apagar).
- Nenhum desses fatos publica em `/iot/sensores/ac/comando`.
- Lista e limiar continuam depois de recriar o serviço a partir do banco.
- UID fora do padrão é recusado. UID novo passa a valer na comparação seguinte.

## Bancada do firmware

1. Confirmar no sketch que não há `192.168.1.2` nem LED RGB nestes pinos. O divisor do LDR está montado e A0 não vê mais do que cerca de 1 V. Gravar host, porta, usuário e senha pelo portal WiFiManager e reiniciar. No Monitor Serial a 115200, o aparelho conecta no broker configurado. Anotar o `lux` no escuro e no claro antes de gravar o limiar.
2. Assentar a chave registrada no escuro. Em até 3 s as duas lâmpadas estão acesas. O ar não muda.
3. Com a mesma chave ainda no suporte, enviar um comando de ar pelo painel ou pela API já existente. O ar responde, o loopback infravermelho continua verificável, e a chave segue reconhecida. A pausa até o loopback não passa de 600 ms, e uma leitura da chave ocorre de novo ao fim dessa janela.
4. Repetir a chegada da chave registrada com o ambiente claro (lux no limiar ou acima). As lâmpadas ficam como estavam.
5. Trocar pela chave desconhecida, de dia e de noite. As duas ficam vermelhas no brilho máximo em até 3 s. O ar não muda.
6. Retirar a chave. Antes de 5 s, as lâmpadas do alerta continuam. Aos 5 s de suporte vazio, as duas apagam em até 3 s.
7. Cobrir o LDR de forma que a amostra não seja publicada (`lux` omitido) e assentar a chave registrada. As lâmpadas não acendem por essa chegada. A chave desconhecida ainda fica vermelha.
8. Remover o UID na API admin, sem regravar o ESP, e assentar de novo. O efeito é o alerta vermelho.

## Critério de pronto do ensaio

SC-001 a SC-007 da spec observáveis na bancada, mais `mvnw.cmd test` verde para a regra com dublês. O painel do suporte não faz parte deste pronto.
