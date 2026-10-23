# Contract: telemetria de presença

Publicado pelo ESP8266. Consumido pelo Spring. Não é comando de lâmpada e não é comando de ar.

## Tópico

`/iot/entrada/presenca`

QoS 0. A mensagem não é retained.

## Quando publicar

- Na hora em que o UID aparece, some ou troca.
- A cada 2 s, o mesmo fato, enquanto o firmware está conectado ao broker.
- Não publicar `present: false` por erro de comunicação do MFRC522. Isso só sai de uma varredura que terminou sem cartão.
- Não publicar este JSON em `/iot/sensores/ac/comando`, `/iot/sensores/ac/estado`, `tuya/lampada_1/command` ou `tuya/lampada_2/command`.

## Payload

Chave lida:

```json
{"present": true, "uid": "04A1B2C3", "lux": 180}
```

Suporte vazio, luz amostrada:

```json
{"present": false, "lux": 800}
```

Chave lida, ADC não amostrado:

```json
{"present": true, "uid": "04A1B2C3"}
```

| Campo | Tipo | Regra |
| --- | --- | --- |
| `present` | boolean | Obrigatório |
| `uid` | string | Obrigatório quando `present` é `true`. Hexadecimal contínuo, 8 a 20 caracteres. Omitido quando `present` é `false` |
| `lux` | number | Opcional. Inteiro de 0 a 1023, valor cru de `analogRead(A0)`. Omitir quando a amostra não existir. Não enviar string |

O firmware envia todo UID que conseguir ler. Ele não envia “registrada” nem “desconhecida”.

## O que o Spring faz com o fato

A decisão está em [data-model.md](../data-model.md). Resumo do contrato de entrada:

- UID normalizado para maiúsculas, sem separadores, comparado à lista.
- `lux` ausente na chegada de morador conta como claro.
- O primeiro `present: false` arma 5 s. Outro `present: false` igual, inclusive o aviso a cada 2 s, não reinicia esse prazo. `present: true` antes do fim cancela.
- `present: false` contínuo por 5 s, contados desde o primeiro vazio, confirma a saída.
- O mesmo `uid` repetido no heartbeat não gera nova ordem às lâmpadas.

## Credenciais do publicador

Host, porta, usuário e senha vêm da configuração gravada pelo WiFiManager na flash. O sketch não contém o endereço `192.168.1.2` nem a senha. Porta omitida usa 1883.
