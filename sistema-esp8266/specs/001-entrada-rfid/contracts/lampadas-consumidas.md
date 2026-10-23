# Contract: lâmpadas já comandadas

A regra de presença não define protocolo novo. Ela chama o serviço de iluminação que já publica na bridge.

## Tópicos que o Spring já usa

- `tuya/lampada_1/command`
- `tuya/lampada_2/command`

Consulta e estado continuam `tuya/{nome}/query` e `tuya/+/state`. O firmware da entrada não publica nesses tópicos.

## Efeitos que a regra pede

Os três efeitos abaixo são pedidos no formato que o serviço já aceita (estado, brilho 0–100, cor `#RRGGBB`). A tradução para DPS permanece a que já foi medida:

| Efeito da regra | Pedido | DPS já existente |
| --- | --- | --- |
| Acender branco no máximo | ligada, brilho 100, `#FFFFFF` | 20 true, 21 `"white"`, 22 na escala 10–1000, 24 HSV |
| Alerta vermelho | ligada, brilho 100, `#FF0000` | 20 true, 21 `"colour"`, 22, 24 |
| Apagar | desligada | 20 false |

Cor com diferença máxima entre R, G e B de até 30 continua sendo branco e modo `"white"`. Esta feature não altera essa regra.

As duas lâmpadas recebem o mesmo pedido em cada transição. Uma falha não cancela o pedido da outra.

Antes de decidir se já estão acesas numa cor normal ou se estão vermelhas, o Spring lê o estado atual das duas em `tuya/+/state` ou por `tuya/{nome}/query`. A ordem desta regra só sai depois dessa leitura. Se a leitura falhar, a chegada de morador no escuro ainda pede acender.

## Fora deste contrato

- `tuyapi` não é dependência do Spring.
- O tópico `/iot/sensores/ac/comando` e o payload `{power, temp}` não são usados pela regra de presença.
- O firmware continua aceitando Coolix nesse tópico, com a espera de loopback limitada a 600 ms.
