# Feature Specification: Entrada por chave RFID

**Feature Branch**: `001-entrada-rfid`

**Created**: 2026-10-21

**Status**: Draft

**Input**: User description: "Chave no suporte da entrada liga as duas lâmpadas da casa quando está escuro e apaga as duas quando sai. Chave desconhecida acende as duas em vermelho e, ao sair, apaga-as. O ar-condicionado não entra nessa regra. O mesmo aparelho que já comanda o ar também lê a chave e a luz."

## Clarifications

### Session 2026-10-21

- Q: Quando a chave entra ou sai do suporte, o que a casa deve fazer sozinha? → A: Chegada no escuro liga as lâmpadas. Saída apaga-as. O ar-condicionado não entra nessa regra.
- Q: Quais chaves podem acender e apagar essas lâmpadas? → A: Só identificadores registrados no sistema. O leitor publica qualquer chave que consiga ler. Chave desconhecida acende as lâmpadas em vermelho.
- Q: Quando as lâmpadas devem sair do vermelho? → A: Ficam vermelhas enquanto a chave desconhecida está no suporte, de dia ou de noite. Ao sair, as lâmpadas apagam, mesmo que estivessem ligadas antes.
- Q: O suporte da chave deve ser um aparelho separado do que já comanda o ar-condicionado? → A: Não. É o mesmo aparelho, com a fiação refeita.
- Q: Quais lâmpadas obedecem a essa regra? → A: As duas lâmpadas da casa, sempre com o mesmo efeito.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Chegar em casa no escuro (Priority: P1)

Um morador coloca no suporte uma chave já registrada. O ambiente está escuro.
As duas lâmpadas da casa acendem. Se já estavam acesas numa cor escolhida pelo
morador, e não havia alerta vermelho, continuam como estavam. O
ar-condicionado não muda.

**Why this priority**: É o motivo do suporte existir. Sem isso, o leitor não
entrega valor.

**Independent Test**: Com uma chave já presente na lista e o ambiente escuro,
assentar a chave deixa as duas lâmpadas acesas, sem alterar o ar-condicionado.

**Acceptance Scenarios**:

1. **Given** as duas lâmpadas apagadas por uso normal, ambiente escuro e a chave registrada fora do suporte, **When** o morador assenta essa chave, **Then** as duas lâmpadas acendem na última cor de utilização normal, ou em branco no brilho máximo se ainda não houver cor lembrada, e o ar-condicionado permanece como estava.
2. **Given** as duas lâmpadas já acesas numa cor que não é o alerta, ambiente escuro e a chave registrada, **When** o morador assenta a chave, **Then** as duas lâmpadas continuam acesas nessa cor.
3. **Given** ambiente claro e qualquer estado das lâmpadas, **When** o morador assenta uma chave registrada, **Then** as lâmpadas não mudam por causa dessa chegada.
4. **Given** uma chave registrada assentada, **When** o morador liga ou desliga o ar-condicionado pelo controle já existente da casa, **Then** o ar obedece a esse controle e a chave continua reconhecida no suporte.

---

### User Story 2 - Sair de casa (Priority: P1)

O morador retira a chave registrada do suporte. Passado o tempo sem nenhuma
chave, as duas lâmpadas apagam.

**Why this priority**: A mesma pessoa que acendeu a casa ao chegar precisa
deixá-la apagada ao sair, sem abrir o painel.

**Independent Test**: Com a chave registrada no suporte e as lâmpadas acesas,
retirar a chave e esperar o tempo de ausência deixa as duas lâmpadas apagadas.

**Acceptance Scenarios**:

1. **Given** a chave registrada no suporte e as duas lâmpadas acesas, **When** o morador retira a chave e o suporte fica vazio pelo tempo de ausência, **Then** as duas lâmpadas apagam.
2. **Given** a chave registrada no suporte, **When** a leitura falha e a mesma chave volta a ser lida antes de completar o tempo de ausência, **Then** as lâmpadas não apagam por essa falha.
3. **Given** as lâmpadas apagadas e a chave registrada no suporte, **When** o morador retira a chave e o tempo de ausência se completa, **Then** as duas lâmpadas permanecem apagadas.

---

### User Story 3 - Chave desconhecida (Priority: P1)

Alguém assenta uma chave que não está na lista. As duas lâmpadas ficam
vermelhas no brilho máximo, de dia ou de noite. Quando essa chave sai e o
suporte fica vazio pelo tempo de ausência, as duas lâmpadas apagam, mesmo que
estivessem acesas antes do alerta.

**Why this priority**: A saída de uma chave apaga a casa. Uma chave qualquer
não pode ser tratada como morador, e a casa precisa mostrar que a tentativa
aconteceu.

**Independent Test**: Assentar uma chave ausente da lista deixa as duas
lâmpadas vermelhas. Retirá-la e esperar o tempo de ausência deixa as duas
apagadas.

**Acceptance Scenarios**:

1. **Given** as lâmpadas em qualquer estado e a qualquer hora, **When** uma chave não registrada é assentada, **Then** as duas lâmpadas ficam vermelhas no brilho máximo e o ar-condicionado não muda.
2. **Given** as lâmpadas vermelhas por uma chave não registrada, **When** essa chave é retirada e o suporte fica vazio pelo tempo de ausência, **Then** as duas lâmpadas apagam.
3. **Given** as lâmpadas acesas numa cor do morador, **When** uma chave não registrada entra e depois sai até completar a ausência, **Then** as lâmpadas terminam apagadas.
4. **Given** uma chave não registrada no suporte e as lâmpadas vermelhas, **When** ela é trocada por uma chave registrada com o ambiente escuro, sem o suporte ficar vazio, **Then** o alerta dessa chave termina e as duas lâmpadas ficam acesas em branco no brilho máximo.
5. **Given** uma chave não registrada no suporte e as lâmpadas vermelhas, **When** ela é trocada por uma chave registrada com o ambiente claro, sem o suporte ficar vazio, **Then** o alerta termina e as duas lâmpadas apagam.

---

### User Story 4 - Cadastrar uma chave (Priority: P2)

Quem já administra a casa inclui ou retira um identificador da lista. A partir
daí, essa chave passa a seguir a regra de morador, ou deixa de seguir, sem
regravar o aparelho do suporte.

**Why this priority**: A lista é o que separa morador de desconhecido. O
primeiro morador pode ser carregado com a casa vazia; incluir o seguinte não
pode depender de uma nova gravação do aparelho.

**Independent Test**: Incluir um identificador novo e assentar a chave
correspondente no escuro acende as lâmpadas. Retirar o identificador da lista
faz a mesma chave, na vez seguinte, acender vermelho.

**Acceptance Scenarios**:

1. **Given** um administrador já autenticado e um identificador ainda fora da lista, **When** ele inclui esse identificador, **Then** a chave correspondente passa a ser tratada como morador na próxima assentada.
2. **Given** um identificador na lista, **When** o administrador o remove, **Then** a próxima assentada dessa chave produz alerta vermelho, e não a regra de morador.
3. **Given** a lista com pelo menos uma chave, **When** o sistema da casa reinicia, **Then** a lista continua valendo.

---

### Edge Cases

- O tempo de ausência conta só com o suporte vazio. Trocar uma chave por outra, sem intervalo vazio, aplica na hora a regra da chave nova e não espera esse tempo.
- O tempo de ausência é de 5 segundos contínuos. Uma leitura intermitente da mesma chave, mais curta que isso, não conta como saída.
- O suporte admite uma chave por vez.
- Se a luminosidade não puder ser lida, a chegada de morador não conta como escuro e não acende as lâmpadas. A chave desconhecida ainda produz vermelho, porque o alerta não depende da luz.
- Se só uma das duas lâmpadas puder ser comandada, a outra ainda recebe a mesma ordem. A casa não trata as duas como uma unidade indivisível na falha.
- Se nenhuma lâmpada puder ser alcançada, elas ficam como estão e a casa não informa sucesso. A transição seguinte de presença envia a ordem de novo.
- Uma ordem do ar-condicionado no meio de uma leitura de chave não cancela a leitura, e a leitura não cancela a ordem do ar.
- Chave ilegível não muda lâmpadas nem ar.
- Depois de um alerta vermelho que terminou em lâmpadas apagadas, a chegada seguinte de um morador no escuro acende as duas em branco no brilho máximo, para o vermelho não voltar como se fosse a cor do morador.
- Chegada de morador no escuro, sem alerta desde a última cor escolhida pelo morador, não troca essa cor.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE reconhecer uma chave assentada no suporte e uma chave ausente.
- **FR-002**: O sistema DEVE medir a luminosidade no momento em que decide uma chegada de morador, no mesmo aparelho que lê a chave e que já comanda o ar-condicionado.
- **FR-003**: O sistema DEVE guardar uma lista durável de identificadores de chave autorizados.
- **FR-004**: Um administrador já autenticado DEVE poder incluir e remover identificadores dessa lista sem alterar o aparelho do suporte.
- **FR-005**: O aparelho do suporte DEVE informar todo identificador que conseguir ler. A decisão de morador ou desconhecido DEVE ocorrer no sistema da casa.
- **FR-006**: Com chave registrada e ambiente escuro, o sistema DEVE deixar as duas lâmpadas acesas. Se não houve alerta desde a última cor do morador, essa cor permanece. Se ainda não houver cor lembrada, ou as lâmpadas estavam apagadas após um alerta, ou estavam vermelhas, elas DEVEM acender em branco no brilho máximo.
- **FR-007**: Com chave registrada e ambiente claro, o sistema NÃO DEVE alterar as lâmpadas por causa dessa chegada.
- **FR-008**: Quando a chave registrada deixa o suporte e ele permanece vazio por 5 segundos, o sistema DEVE apagar as duas lâmpadas.
- **FR-009**: Uma falha de leitura mais curta que 5 segundos, seguida da mesma chave, NÃO DEVE apagar as lâmpadas.
- **FR-010**: Com chave não registrada no suporte, o sistema DEVE deixar as duas lâmpadas vermelhas no brilho máximo, de dia ou de noite, até que essa presença termine.
- **FR-011**: Quando a chave não registrada deixa o suporte e ele permanece vazio por 5 segundos, o sistema DEVE apagar as duas lâmpadas, qualquer que fosse o estado anterior.
- **FR-012**: Troca direta de uma chave por outra, sem o suporte ficar vazio, DEVE encerrar a regra da chave anterior e aplicar a regra da chave nova sem esperar 5 segundos.
- **FR-013**: Nenhuma transição de chave DEVE ligar, desligar ou alterar a temperatura do ar-condicionado.
- **FR-014**: Um comando de ar-condicionado feito pelo controle já existente DEVE continuar possível com uma chave no suporte, e uma leitura de chave DEVE continuar possível durante esse comando.
- **FR-015**: As duas lâmpadas DEVEM receber sempre o mesmo efeito nesta regra.
- **FR-016**: Se a luminosidade não estiver disponível, a chegada de morador DEVE ser tratada como ambiente claro.
- **FR-017**: O limiar que separa claro de escuro DEVE ser ajustável no sistema da casa e DEVE valer depois de reinício. O número inicial fica para calibração no local.
- **FR-018**: O sistema NÃO DEVE informar sucesso de uma ordem às lâmpadas quando não conseguir alcançá-las.

### Key Entities

- **Chave**: Identificador lido no suporte. Está registrada ou não. Uma chave registrada tem dono implícito na lista da casa; o nome da pessoa é opcional.
- **Suporte**: Um encaixe físico, uma chave por vez. Estados: vazio, ocupado por chave registrada, ocupado por chave desconhecida.
- **Leitura de luz**: Valor medido na chegada e o limiar configurado. Resultado usado pela regra: escuro ou claro. Sem leitura, a regra usa claro.
- **Lâmpada**: Cada uma das duas lâmpadas da casa. Atributos visíveis: ligada ou apagada, cor, brilho. As duas recebem a mesma ordem. A última cor de utilização normal é lembrada à parte da cor de alerta.
- **Alerta**: Presença atual de chave não registrada. Enquanto dura, as duas lâmpadas ficam vermelhas. Termina quando essa chave deixa de ser a chave do suporte.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com ambiente escuro e chave registrada, as duas lâmpadas estão acesas em até 3 segundos depois de a chave assentar.
- **SC-002**: Depois de 5 segundos contínuos de suporte vazio, as duas lâmpadas estão apagadas em até 3 segundos.
- **SC-003**: Com chave não registrada, as duas lâmpadas estão vermelhas no brilho máximo em até 3 segundos, tanto de dia quanto de noite.
- **SC-004**: Chegada de chave registrada em ambiente claro deixa o estado das lâmpadas igual ao de antes, em 100% dos ensaios dessa condição.
- **SC-005**: Em 100% dos ensaios, assentar ou retirar uma chave não altera ligado, desligado ou temperatura do ar-condicionado.
- **SC-006**: Um comando de ar-condicionado feito com a chave no suporte ainda produz efeito observável no ar, e a chave permanece reconhecida.
- **SC-007**: Incluir ou remover um identificador na lista passa a valer na assentada seguinte, sem regravar o aparelho, e a lista permanece depois de reiniciar o sistema da casa.

## Assumptions

- O tempo sem leitura que confirma a saída é de 5 segundos. Esse número não foi medido em bancada; é a predefinição aceita para a primeira versão.
- "As duas lâmpadas" são as duas lâmpadas que a casa já controla hoje.
- Há um único suporte, fisicamente no mesmo aparelho que já envia o sinal do ar-condicionado. A fiação desse aparelho será refeita no plano. Não há segundo leitor.
- O primeiro identificador pode ser inserido por quem administra a casa. A forma da tela ou do comando fica no plano; o resultado visível é o da User Story 4.
- Brilho máximo do alerta é o maior brilho que essas lâmpadas já aceitam. Branco da chegada depois de um alerta também usa esse brilho.
- Chegada de morador no escuro, fora do caso de alerta, preserva a cor. Se nenhuma cor de utilização normal foi lembrada ainda, as lâmpadas acendem em branco no brilho máximo. O vermelho do alerta não conta como cor do morador.
- O limiar de escuro começa configurável e sem valor de fábrica fechado, até calibração no suporte real. Enquanto não houver calibração, o ensaio usa um limiar combinado para o sensor daquele aparelho.
- Assistente de voz, descoberta do aparelho por voz e ligar ou desligar o ar pela chegada ou pela saída da chave estão fora desta feature.
- O controle manual das lâmpadas pelo painel, fora dos momentos de chegada, saída e alerta, continua disponível e não é redefinido aqui.
- Quem cadastra chaves é o administrador já autenticado no sistema da casa. Não há perfil novo de visitante.
