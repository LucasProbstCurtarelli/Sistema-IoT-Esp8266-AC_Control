# Data Model: Entrada por chave RFID

Entidades da spec, no formato que o orquestrador persiste ou calcula. O firmware não guarda esta lista nem o limiar.

## Chave autorizada

Tabela nova, próxima migração Flyway depois de `V5__create_light_link_map`.

| Campo | Regras |
| --- | --- |
| `id` | UUID, chave primária |
| `uid` | Obrigatório, único, hexadecimal maiúsculo, 8 a 20 caracteres, sem `:` ou espaços. É o identificador que o MFRC522 leu |
| `rotulo` | Opcional, até 100 caracteres. Nome da pessoa; a spec trata o nome como opcional |
| `created_at` | Preenchido na inclusão |

Relação: uma linha por chave que pode seguir a regra de morador. Chave lida que não está na tabela é desconhecida. Remover a linha faz a assentada seguinte cair no alerta vermelho.

Validação: rejeitar UID fora do padrão. Normalizar minúsculas e separadores antes de gravar e antes de comparar.

## Ajuste da entrada

Uma única linha. Sobrevive a reinício.

| Campo | Regras |
| --- | --- |
| `id` | Constante da linha única |
| `lux_threshold` | Inteiro anulável, 0 a 1023. Nulo significa ainda sem calibração |
| `normal_color` | `#RRGGBB` ou nulo. Última cor de uso normal. Nunca o vermelho do alerta |
| `normal_brightness` | 0 a 100, ou nulo. Brilho que acompanha `normal_color` |
| `prefer_white` | Booleano. Verdadeiro depois que um alerta terminou com as lâmpadas apagadas. A chegada seguinte de morador no escuro usa branco no brilho máximo e então o marcador volta a falso |

Estado inicial: limiar nulo, cor nula, `prefer_white` falso.

## Leitura (fato, não é tabela)

Mensagem em `/iot/entrada/presenca`. Ver [contracts/presenca-mqtt.md](./contracts/presenca-mqtt.md).

| Campo | Regras |
| --- | --- |
| `present` | `true` com cartão cujo UID foi lido; `false` só quando a varredura conclui sem cartão |
| `uid` | Presente apenas com `present: true`. Hex sem separador. O Spring normaliza para maiúsculas |
| `lux` | Inteiro 0–1023, ou ausente se o ADC não foi amostrado |

O último fato fica em memória no serviço de presença para o painel consultar no futuro. Não é a lista de chaves e se perde no reinício; o heartbeat seguinte o reconstitui.

## Suporte (estado calculado)

Um suporte, uma chave por vez.

| Estado | Como se entra | Efeito nas lâmpadas | Ar |
| --- | --- | --- | --- |
| Vazio | `present: false` contínuo por 5 s | As duas apagam | Não muda |
| Vazio pendente | Primeiro `present: false` depois de ocupado | Nenhum ainda | Não muda |
| Ocupado por registrada | `present: true` e UID na lista | Ver regra abaixo | Não muda |
| Ocupado por desconhecida | `present: true` e UID fora da lista | As duas vermelhas, brilho 100, de dia ou de noite | Não muda |

Transições:

- Vazio pendente mais outro `present: false`, inclusive o aviso a cada 2 s: o prazo não reinicia. Continua a contar desde o primeiro vazio.
- Vazio pendente mais `present: true` antes de 5 s, mesmo UID: cancela o timer. As lâmpadas não apagam por essa falha.
- Vazio pendente completa 5 s: passa a vazio e apaga as duas. Se o ocupante era desconhecido, `prefer_white` fica verdadeiro.
- Troca de UID sem `present: false`: encerra a regra anterior na hora. Não espera 5 s.
- Desconhecida seguida de registrada, sem vazio, no escuro: alerta termina e as duas acendem branco no brilho máximo.
- Desconhecida seguida de registrada, sem vazio, no claro ou sem `lux`: alerta termina e as duas apagam. `prefer_white` fica verdadeiro.
- Registrada seguida de outra registrada, no claro: a chegada nova não muda as lâmpadas.
- Registrada seguida de desconhecida: vermelho nas duas na hora.

Regra da chegada registrada, avaliada só na transição (não no heartbeat do mesmo UID):

- Escuro = `lux` numérico, `lux_threshold` definido, `lux < lux_threshold`.
- Sem `lux`, ou limiar nulo: tratar como claro.
- Claro: não enviar ordem por causa dessa chegada, exceto no caso da troca que encerra alerta (linha da tabela acima).
- “Já acesas” e “vermelhas” vêm de `tuya/+/state` ou de uma consulta às duas, lida antes da ordem. Leitura falhada não impede acender no escuro.
- Escuro, as duas já acesas numa cor que não é o alerta, e `prefer_white` falso: não enviar ordem.
- Escuro, e (`prefer_white`, ou estavam vermelhas, ou não há cor lembrada, ou estavam apagadas): acender as duas em branco no brilho máximo se `prefer_white` ou se não há cor lembrada ou se estavam vermelhas; senão acender as duas na `normal_color` / `normal_brightness`.
- Consulta de estado que falhe não impede a ordem de acender no escuro. As duas ainda recebem essa ordem.
- Falha ao comandar uma lâmpada não cancela a ordem da outra. Falha das duas não conta como sucesso.

Cor normal:

- Comando manual já existente, concluído, com cor diferente do alerta, grava `normal_color` e `normal_brightness` e zera `prefer_white`.
- A ordem vermelha da própria regra não grava cor normal.
- A ordem branca emitida por `prefer_white` passa a ser a cor lembrada e zera o marcador, para o vermelho não voltar na chegada seguinte.

## Lâmpada

As duas lâmpadas já cadastradas, `lampada_1` e `lampada_2`. Esta feature não cria dispositivo novo e não muda DPS. Atributos usados pela regra: ligada ou apagada, cor, brilho. Os dois aparelhos recebem sempre o mesmo efeito.

## Fora do modelo

O ar-condicionado não ganha coluna nem estado nesta feature. Comando de ar continua no contrato Coolix já existente, independente do estado do suporte.
