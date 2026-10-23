# Contract: lista de chaves e limiar

API do orquestrador para o administrador já autenticado. Não há tela nesta leva; o contrato existe para a User Story 4 e para o limiar de FR-017.

Autenticação igual à dos outros endpoints admin: `/api/admin/**` exige `ROLE_ADMIN`. Chamada sem credencial recebe o 401 já usado no restante da API. Credencial sem esse papel recebe 403.

## Chaves

`GET /api/admin/entrada/chaves`

```json
{"chaves": [{"uid": "04A1B2C3", "rotulo": "Morador"}]}
```

Lista vazia é válida: `{"chaves": []}`.

`POST /api/admin/entrada/chaves`

```json
{"uid": "04a1b2c3", "rotulo": "Morador"}
```

`rotulo` pode ser omitido ou nulo. O servidor grava o UID em maiúsculas. Resposta 201 com o registro gravado. UID repetido responde 409. UID fora de `^[0-9A-Fa-f]{8,20}$` responde 400.

`DELETE /api/admin/entrada/chaves/{uid}`

`{uid}` aceita o hex em qualquer caixa. 204 quando remove. 404 quando o UID não está na lista.

A linha removida deixa de ser morador na próxima assentada. Reiniciar o Spring não apaga a tabela.

## Limiar

`GET /api/admin/entrada/limiar`

```json
{"threshold": null}
```

`null` é o estado inicial: chegada de morador não conta como escuro.

`PUT /api/admin/entrada/limiar`

```json
{"threshold": 350}
```

`threshold` é inteiro de 0 a 1023, ou `null` para voltar ao estado sem calibração. Fora dessa faixa responde 400. O valor gravado vale depois do reinício.

Escuro, para a regra, é `lux < threshold` com os dois números presentes. O valor igual ao limiar conta como claro.

## O que esta API não faz

Não comanda lâmpada, não comanda ar e não recebe o UID ao vivo do suporte. O fato ao vivo chega só por [presenca-mqtt.md](./presenca-mqtt.md).
