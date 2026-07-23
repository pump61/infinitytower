# InfinityTower

Plugin para **Paper/Spigot 1.21** que implementa um sistema de "torre infinita" (dungeon PvE por andares progressivos), com modos **Solo** e **Party**, sistema de chaves de acesso, ranking/recordes, estatísticas de jogador, menus por inventário e um sistema de setup in-game para criar arenas sem editar YAML na mão.

- **Grupo/artefato:** `com.eternity:InfinityTower`
- **Versão:** 1.0
- **Java:** 21
- **API:** Paper 1.21 (`api-version: 1.21`)

## Dependências

| Plugin | Tipo | Uso |
|---|---|---|
| [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) | **depend** (obrigatório) | Expõe os placeholders `%infinitytower_...%` |
| [MythicMobs](https://www.mythicmobs.net/) | soft-depend | Spawn de mobs customizados nos andares (`mythic:` no YAML da dungeon) |
| [Citizens](https://www.citizensnpcs.co/) | soft-depend | Declarado no `plugin.yml`; sem integração de código no momento |
| [EssentialsX](https://essentialsx.net/) | soft-depend | Sobrescreve o `/back` do Essentials ao sair da dungeon (`EssentialsBackUtil`). O plugin também bloqueia `/back` por alguns segundos por conta própria (`tower.back_block_seconds`), então essa proteção funciona mesmo sem o Essentials instalado |
| SQLite JDBC / MySQL Connector/J | embutido (shaded) | Persistência de stats, ranking e histórico de runs |

## Instalação

1. Compile com Maven (`mvn package`) ou baixe o `.jar` já buildado em `target/InfinityTower-1.0.jar`.
2. Coloque o jar em `plugins/` do servidor Paper, junto com `PlaceholderAPI.jar` (obrigatório).
3. Suba o servidor. Na primeira execução o plugin cria em `plugins/InfinityTower/`:
   - `config.yml`
   - `menus.yml`
   - `lang/pt-BR.yml` (idioma definido em `config.yml: lang`)
   - `dungeons/solo_10.yml` e `dungeons/party_10.yml` (templates de exemplo)
4. Use `/tower admin setup <dungeonId>` para configurar spawns e pontos de mob de uma dungeon direto in-game (veja [Setup de arenas](#setup-de-arenas-admin)).

## Comandos

Comando raiz: **`/tower`** (alias: `/torre`).

### Jogador (`infinitytower.player`, default: todos)

| Comando | Descrição |
|---|---|
| `/tower` ou `/tower help` | Mostra a lista de comandos disponíveis |
| `/tower menu` | Abre o menu principal (escolha Solo/Party) |
| `/tower stats` | Abre o menu de estatísticas pessoais |
| `/tower party invite <player>` | Convida um jogador para a sua party |
| `/tower party accept` | Aceita o convite pendente |
| `/tower party leave` | Sai da party (sem cancelar a dungeon em andamento) |
| `/tower party disband` | Desfaz a party (somente líder) |
| `/tower party promote <player>` | Transfere a liderança (somente líder) |
| `/tower party kick <player>` | Expulsa um membro (somente líder) |
| `/tower invite <player>` / `/tower accept` | Aliases legados de `party invite`/`party accept` |
| `/tower leave` | Sai da dungeon/run atual |
| `/tower top <wins\|time\|floor> <dungeonId> [limit]` | Ranking de vitórias, melhor tempo ou andar mais alto alcançado (limite 1–50, padrão 10) |
| `/tower record <dungeonId>` | Recorde individual e de party daquela dungeon |

### Admin (`infinitytower.admin`, default: op)

| Comando | Descrição |
|---|---|
| `/tower menu <solo\|party> [player]` | Força a abertura de um submenu para si ou outro jogador |
| `/tower give key <player> <dungeonId> [amount]` | Dá chave(s) de acesso a um jogador online |
| `/tower giveall key <dungeonId> [amount]` | Dá chave(s) a todos os jogadores online |
| `/tower admin reload` | Recarrega todas as dungeons de `dungeons/*.yml` |
| `/tower admin list` | Lista as dungeons carregadas |
| `/tower admin create <dungeonId>` | Cria uma dungeon nova a partir do template (`party_10.yml` se o id começar com `party`, senão `solo_10.yml`) |
| `/tower admin setup <dungeonId> [arena]` | Inicia uma sessão de setup (arena padrão = `1`) |
| `/tower admin where` | Mostra em qual setup você está |
| `/tower admin setspawn <solo\|leader>` | Define o spawn único de solo ou do líder da party |
| `/tower admin addspawn <solo\|members>` | Adiciona um spawn à lista (spawns solo ou membros da party) |
| `/tower admin clearspawns <solo\|members>` | Limpa a lista de spawns |
| `/tower admin setreturn` | Define o ponto de retorno ao fim/saída da dungeon |
| `/tower admin mobspawn <add\|set\|clear> <floor>` | Gerencia os pontos de spawn de mob de um andar |
| `/tower admin save` | Salva e recarrega a dungeon em edição |
| `/tower admin cancel` | Cancela o setup atual sem salvar |

Tab-completion está implementado para todos os subcomandos (incluindo nomes de jogadores online e IDs de dungeon).

## Permissões

| Permissão | Default | Descrição |
|---|---|---|
| `infinitytower.player` | `true` | Comandos básicos (menu, stats, party, top, record, leave) |
| `infinitytower.admin` | `op` | Comandos administrativos e de setup |
| `infinitytower.*` | `op` | Concede `player` + `admin` |

## Placeholders (PlaceholderAPI)

Identificador da expansion: `infinitytower` → use como `%infinitytower_<param>%`.

### Estatísticas do jogador (`OfflinePlayer`)

| Placeholder | Retorna |
|---|---|
| `%infinitytower_runs%` | Total de runs (solo + party) |
| `%infinitytower_wins%` | Total de vitórias |
| `%infinitytower_losses%` | Total de derrotas |
| `%infinitytower_solo_runs%` / `solo_wins` / `solo_losses` | Estatísticas apenas do modo solo |
| `%infinitytower_party_runs%` / `party_wins` / `party_losses` | Estatísticas apenas do modo party |

### Ranking / recordes (não dependem do player alvo)

| Placeholder | Retorna |
|---|---|
| `%infinitytower_top_wins_<pos>_<dungeonId>_name%` | Nome do jogador na posição `<pos>` do ranking de vitórias daquela dungeon |
| `%infinitytower_top_wins_<pos>_<dungeonId>_value%` | Quantidade de vitórias dessa posição |
| `%infinitytower_top_floor_<pos>_<dungeonId>_name%` | Nome do jogador na posição `<pos>` do ranking de andar mais alto alcançado |
| `%infinitytower_top_floor_<pos>_<dungeonId>_value%` | Andar mais alto alcançado por essa posição |
| `%infinitytower_best_player_<dungeonId>_name%` | Nome do melhor tempo individual da dungeon |
| `%infinitytower_best_player_<dungeonId>_time_ms%` | Melhor tempo individual em milissegundos |
| `%infinitytower_best_party_<dungeonId>_leader%` | Líder da melhor run em party |
| `%infinitytower_best_party_<dungeonId>_time_ms%` | Melhor tempo em party (ms) |

> Exemplo: `%infinitytower_top_wins_1_solo_10_name%` — nome do #1 do ranking de vitórias da dungeon `solo_10`.
> Posições sem registro retornam `-` (texto) ou `0` (valor numérico).
> `<dungeonId>` pode conter underscore (`solo_10`, `party_10`, etc.) sem problema — o parsing isola só a posição (`<pos>`) e o campo final (`name`/`value`/`time_ms`/`leader`), o resto vira o id da dungeon.

## Estrutura de arquivos (`plugins/InfinityTower/`)

```
plugins/InfinityTower/
├── config.yml            # configuração global do plugin
├── menus.yml             # layout dos menus de inventário
├── lang/
│   └── pt-BR.yml          # todas as mensagens (idioma definido em config.yml: lang)
├── dungeons/
│   ├── solo_10.yml        # template padrão de dungeon solo
│   ├── party_10.yml       # template padrão de dungeon party
│   └── <seus>.yml         # criadas via /tower admin create
└── database.db            # SQLite (se database.type: sqlite)
```

### `config.yml`

Principais chaves:

- `lang` — código do idioma (procura `lang/<código>.yml`; cai para `pt-BR` se não achar).
- `prefix` — prefixo usado em mensagens gerais.
- `tower.title_times` — `fade_in_ticks` / `stay_ticks` / `fade_out_ticks` dos titles de andar/entrada/conclusão.
- `tower.tracking.spawn_radius` — raio (em blocos) para capturar minions gerados por mobs do MythicMobs (ex.: adds de um boss) como parte do andar.
- `dungeon.command_whitelist` — lista de comandos permitidos dentro da dungeon (o resto é bloqueado por `CommandWhitelistListener`).
- `main-menu` (ou `main_menu`) — controla o **menu principal** (`/tower menu`): botões **Solo**/**Party**, filler, título. Essa é a única fonte real desse menu (ver correção na seção [`menus.yml`](#menusyml) logo abaixo).
- `tower.enter_teleport_delay_seconds` — delay global (segundos), aplicado a **todas** as dungeons, entre abrir o menu/clicar e teleportar o jogador para a arena (dá tempo de carregar chunks, HUD, etc.). Padrão: `2` se a chave não existir.
- `tower.back_block_seconds` — segundos em que `/back` (e variações: `/eback`, `/cback`, `essentials:back`, `cmi:back`) ficam bloqueados após o jogador sair/terminar a dungeon. Funciona independente de ter EssentialsX instalado. Padrão: `5`.
- `spawn` — spawn global de fallback quando uma dungeon não tem `return_spawn`/`player_spawns` configurado (ou o mundo configurado não existe).
- `database.*` — ver seção [Banco de dados](#banco-de-dados).
- `debug.enabled` — liga logs extras de depuração.

### `menus.yml`

Controla duas telas (o menu principal **não** é uma delas — veja a nota abaixo):

- `player_stats_menu` — tela do `/tower stats`. `items.<qualquer_nome>` vira um item no slot configurado, com placeholders `{player} {uuid} {soloRuns} {soloWins} {soloLosses} {partyRuns} {partyWins} {partyLosses} {totalRuns} {totalWins} {totalLosses}` disponíveis em `title`/`name`/`lore` de qualquer item (não só `solo`/`party`/`total`, dá pra adicionar quantos quiser).
- `menus.solo` / `menus.party` — telas que listam as dungeons daquele modo nos slots definidos em `dungeon-slots`, usando o template `dungeon-item` com placeholders `{id} {display} {mode} {max_floors}`.

Cada item aceita `material`, `name`, `lore`, `custom_model_data` e (nos menus de dungeon) `hide_attributes`. `filler` preenche os slots vazios do inventário.

> **Nota sobre o menu principal (`/tower menu`):** ele é controlado por `config.yml: main-menu`, **não** por `menus.yml`. Até uma revisão recente, `menus.yml` tinha uma seção `main_menu:` e `config.yml` tinha uma seção `stats-menu:` que pareciam configurar essas telas, mas nenhuma das duas nunca era lida pelo código — eram sobras mortas de uma versão anterior. Ambas foram removidas dos arquivos padrão; o menu de stats agora é de fato lido de `menus.yml: player_stats_menu` (antes disso era todo fixo no Java, ignorando qualquer configuração).

### `lang/pt-BR.yml`

Todas as mensagens do plugin, organizadas em seções: `titles` (titles de tela), `messages` (mensagens gerais), `party` (sistema de party), `usage` (mensagens de uso incorreto), `help` (texto de `/tower help`), `database`, `setup` e `logs` (mensagens de console). Edite livremente — os placeholders `{...}` de cada linha são substituídos pelo código na hora do envio.

### Dungeons / mapas (`dungeons/<id>.yml`)

Cada arquivo YAML dentro de `plugins/InfinityTower/dungeons/` descreve um **mapa/dungeon** completo: identidade, chave de acesso, spawns, titles, e o conteúdo de cada andar (mobs + recompensas). O nome do arquivo (sem `.yml`) vira o `dungeonId` usado em `/tower give key`, `/tower top`, `/tower record`, `/tower admin setup`, etc. Um mesmo arquivo também pode conter **arenas alternativas** e **dungeons extras** — ver [Arenas alternativas e dungeons extra](#arenas-alternativas-e-dungeons-extra) mais abaixo.

#### Referência completa de chaves (nível raiz)

| Chave | Tipo | Padrão | Descrição |
|---|---|---|---|
| `display` | texto | `id do arquivo` | Nome exibido (aceita `&cor` e hex `&#RRGGBB`). Usado no menu, no item-chave, em `/tower top`/`/tower record` e no placeholder `{dungeon}` do title de conclusão |
| `mode` | `SOLO` ou `PARTY` | `SOLO` | Define em qual menu (`/tower menu solo` ou `/tower menu party`) a dungeon aparece e as regras de entrada — ver [Acesso e regras solo/party](#acesso-e-regras-soloparty) |
| `max_floors` | número | `10` | Quantidade de andares; ao passar do último a dungeon é finalizada (`onFloorCleared` detecta `floor > max_floors`) |
| `allow_empty_floors` | `true`/`false` | `false` | Se `false`, um andar sem nenhum mob configurado (ou cujos mobs falharem ao spawnar) **cancela a run** com a mensagem `floor_no_mobs`/`floor_spawn_failed`. Se `true`, o andar é tratado como "vazio" e a run segue |
| `start_delay_seconds` | segundos | `10` | Tempo entre o title `on_enter` (ao clicar pra entrar) e o início efetivo do andar 1 — durante esse tempo o title `preparing` também é mostrado |
| `next_floor_delay_seconds` | segundos | `5` | Tempo de espera entre um andar ser limpo e o próximo começar |
| `allow_solo_in_party` | `true`/`false` | `true` | Só vale para dungeons `mode: SOLO`. Se `true` (padrão), um jogador em party ainda pode entrar sozinho numa dungeon SOLO (a run é só dele, o resto da party não é afetado). Se `false`, jogadores em party são bloqueados no menu SOLO (`solo_requires_no_party`) até saírem da party — veja a tabela da próxima seção |
| `access.*` | seção | — | Chave de acesso e limitador de entradas — ver [Acesso e entrada](#acesso-e-entrada-chaves-e-limitador) |
| `menu-item.*` | seção | — | Ícone da dungeon nos menus solo/party — ver [Aparência no menu](#aparência-no-menu-menu-item) |
| `player_spawns` | lista de locais | — | Pontos de spawn ao entrar **sozinho** (SOLO, ou PARTY sem estar em party) — um é sorteado aleatoriamente a cada entrada |
| `party_leader_spawn` | local único | — | Spawn do **líder** quando a run é iniciada em party |
| `party_member_spawns` | lista de locais | — | Spawns dos **membros** (não-líder) em uma run de party — um é sorteado aleatoriamente por membro |
| `return_spawn` | local único | cai no `spawn` global do `config.yml` | Para onde o(s) jogador(es) voltam ao concluir a dungeon, morrer (fim de run) ou usar `/tower leave` |
| `titles.*` | seção | — | Textos de title/subtitle da dungeon — ver [Titles e andar de boss](#titles-e-andar-de-boss) |
| `floors.<n>.mobs` | lista | — | Mobs do andar `n` — ver [Configuração de mobs](#configuração-de-mobs-vanilla-vs-mythicmobs) |
| `floors.<n>.boss` | `true`/`false` | `false` | Marca o andar `n` como andar de boss — troca os titles usados (ver titles) |
| `floors.<n>.boss_name` | texto | `""` | Preenche o placeholder `{boss_name}` nos titles desse andar |
| `floors.<n>.titles.*` | seção | — | Override de title **somente para esse andar** — mesmos grupos de `titles.*` (menos `on_enter`/`preparing`/`completed`, que são só de nível raiz) |
| `floors.<n>.rewards` | seção | — | Recompensas ao limpar o andar — ver [Recompensas](#recompensas-por-andar) |

Cada uma dessas áreas é detalhada nas subseções abaixo.

#### Fluxo de execução de uma run

Sequência real de eventos (código: [`DungeonSession`](src/main/java/com/eternity/infinitytower/tower/session/DungeonSession.java)), do clique no menu até o fim:

1. Jogador clica na dungeon no menu → checagem de acesso (chave/limiter, ver abaixo) → `DungeonSession.start()`.
2. Title `titles.on_enter` é mostrado imediatamente.
3. Espera `tower.enter_teleport_delay_seconds` (config.yml, global).
4. Jogador(es) são teleportados para `player_spawns` (solo) ou `party_leader_spawn`/`party_member_spawns` (party).
5. Title `titles.preparing` é mostrado (placeholders `{floor}`=1, `{delay}`=`start_delay_seconds`).
6. Espera `start_delay_seconds`.
7. Andar 1 começa: mobs do `floors.1.mobs` são spawnados; title `floor_started`/`boss_floor_started` (conforme `floors.1.boss`) é mostrado.
8. Quando todos os mobs do andar morrem: `floors.<n>.rewards` é entregue a quem está vivo → a cada 10 andares limpos (10, 20, 30…) é feito um **broadcast pro servidor inteiro** (`messages.dungeon_broadcast_floor10`) → title `floor_cleared`/`next_is_boss` (conforme o **próximo** andar ser boss) é mostrado → espera `next_floor_delay_seconds` → repete a partir do passo 7 para o próximo andar.
9. Se **todos** os jogadores online morrerem no mesmo andar antes de limpá-lo, a run falha (`dungeon_all_dead`) e encerra.
10. Ao limpar o último andar (`floor > max_floors`): title `titles.completed` (placeholder `{dungeon}` = `display`) e teleporte para `return_spawn`.
11. `/tower leave` a qualquer momento também encerra a run e teleporta para `return_spawn`.

#### Acesso e regras solo/party

O `mode` da dungeon decide o comportamento no menu (código: [`MenuListener`](src/main/java/com/eternity/infinitytower/listener/MenuListener.java)):

| `mode` | Jogador sem party | Jogador em party (líder ou membro) |
|---|---|---|
| `SOLO` | Entra normalmente sozinho | Depende de `allow_solo_in_party` da dungeon: `true` (padrão) → entra sozinho normalmente, sem afetar o resto da party; `false` → **bloqueado**, mensagem `solo_requires_no_party` (precisa sair da party) |
| `PARTY` | Entra sozinho (run "solo" numa dungeon party) | **Líder**: inicia a run para toda a party. **Membro**: bloqueado no menu — só o líder inicia (`party_only_leader_start_menu`) |

O menu solo só lista dungeons `mode: SOLO`; o menu party só lista `mode: PARTY` — dá pra ter as duas versões da "mesma" dungeon (uma de cada `mode`) usando o recurso de [dungeons extra](#arenas-alternativas-e-dungeons-extra) no mesmo arquivo, se quiser.

#### Acesso e entrada (chaves e limitador)

```yaml
access:
  require_key: true       # exige o item-chave no inventário pra entrar
  consume_key: true        # consome 1 unidade da chave ao entrar (ignorado se require_key: false)
  key:
    material: PAPER
    custom-model-data: 10133
    name: "&bChave da {display}"
    lore:
      - "&7Acesso: &f{id}"
      - "&7Andares: &f{max_floors}"
    glow: true              # brilho falso (encantamento oculto), não é um encantamento real
  limiter:
    enabled: false
    cooldown_seconds: 0        # 0 = sem cooldown entre entradas
    max_entries_per_day: 0     # 0 = sem limite diário
```

- A chave (`access.key`) é só **aparência** de item (material/nome/lore/model data/glow) — a identificação real de "isso é a chave da dungeon X" é feita via `PersistentDataContainer`, não pelo material. `{id}`, `{display}` e `{max_floors}` são substituídos na hora de gerar o item (`/tower give key`, `/tower giveall key`).
- Sem `access.key` configurado, `/tower give key` avisa `key_not_configured` e não entrega nada.
- `access.limiter` é por jogador e por dungeon: `cooldown_seconds` bloqueia reentrada por N segundos após sair, `max_entries_per_day` conta entradas que resetam à meia-noite (hora do servidor). Os dois podem ser usados juntos.
- Ordem de checagem ao entrar: **chave presente?** → **limiter (cooldown/limite diário) OK?** → **consome a chave** (se `consume_key: true`). Se qualquer etapa falhar, o jogador não entra e nada é consumido.

#### Aparência no menu (`menu-item`)

```yaml
menu-item:
  material: ENDER_EYE
  custom-model-data: 0
  name: "&a&l{display}"
  lore:
    - "&7Modo: &f{mode}"
    - "&7Andares: &f{max_floors}"
```

- Se a dungeon **não** tiver `menu-item`, o menu usa o template padrão em `menus.yml` (`menus.solo.dungeon-item` / `menus.party.dungeon-item`) — então normalmente você só precisa definir `menu-item` quando quiser um ícone **diferente** do padrão para aquela dungeon específica.
- Truque para **esconder** uma dungeon do menu sem excluí-la: configure `menu-item.material: AIR` (ou o template padrão como `AIR`) — a dungeon é pulada na montagem do menu, mas continua jogável via `/tower menu solo|party` administrativo, `/tower give key`, etc.
- Placeholders disponíveis no `name`/`lore`: `{id}`, `{display}`, `{mode}`, `{max_floors}`.

#### Titles e andar de boss

Dois grupos de titles existem: os de **nível raiz** (só podem ser definidos uma vez, no topo do arquivo) e os **por-andar-com-fallback-pro-raiz** (podem ser sobrescritos por andar específico em `floors.<n>.titles.<grupo>`, caindo para `titles.<grupo>` da raiz se o andar não tiver override, e caindo para um texto padrão do `lang/pt-BR.yml` se nem a raiz tiver).

| Grupo | Nível | Quando aparece | Placeholders |
|---|---|---|---|
| `titles.on_enter` | só raiz | Ao clicar pra entrar, antes do teleporte | `{floor}` (sempre 1), `{delay}` = `start_delay_seconds` |
| `titles.preparing` | só raiz | Logo após o teleporte, antes do andar 1 começar | `{floor}` (sempre 1), `{delay}` = `start_delay_seconds` |
| `titles.floor_started` / `floors.<n>.titles.floor_started` | raiz + por-andar | Início de um andar **normal** (`floors.<n>.boss` ausente/`false`) | `{floor}`, `{boss_name}` |
| `titles.boss_floor_started` / `floors.<n>.titles.boss_floor_started` | raiz + por-andar | Início de um andar marcado `boss: true` | `{floor}`, `{boss_name}` |
| `titles.floor_cleared` / `floors.<n>.titles.floor_cleared` | raiz + por-andar | Andar limpo e o **próximo** andar NÃO é boss | `{floor}` (andar limpo), `{delay}` = `next_floor_delay_seconds`, `{boss_name}` |
| `titles.next_is_boss` / `floors.<n>.titles.next_is_boss` | raiz + por-andar | Andar limpo e o **próximo** andar É boss (usa os textos do andar de boss que vem a seguir) | `{floor}` (andar limpo), `{delay}` = `next_floor_delay_seconds`, `{boss_name}` (do próximo andar) |
| `titles.completed` | só raiz | Dungeon inteira concluída (passou do `max_floors`) | `{dungeon}` = `display` |

Os templates padrão (`solo_10.yml`/`party_10.yml`) só configuram `on_enter`, `preparing`, `floor_cleared` e `completed` — `floor_started`, `boss_floor_started` e `next_is_boss` funcionam com o texto padrão do idioma até você querer customizá-los.

**Andar de boss**, exemplo:

```yaml
floors:
  10:
    boss: true
    boss_name: "&4&lRei Esqueleto"
    titles:
      boss_floor_started:
        title: "&4&l{boss_name}"
        subtitle: "&cSobreviva!"
    mobs:
      - mythic: SkeletonKing
        amount: 1
        spawns: [...]
```

Configurar `floors.<n>.boss: true` sem `titles` próprios ainda troca automaticamente o title exibido para o grupo `boss_floor_started`/`next_is_boss` (usando o texto padrão de `titles.boss_floor_started` da raiz, se existir, senão o do `lang/pt-BR.yml`).

#### Recompensas por andar

```yaml
floors:
  1:
    rewards:
      enabled: true
      items:
        - type: DIAMOND
          amount: 1
      commands:
        - "give {player} emerald 2"
        - "broadcast &6{player} completou o andar!"
```

- `enabled` — se `false` (ou a seção `rewards` ausente), nada é entregue nesse andar.
- `items` — lista de `type` (nome de `Material`) + `amount`; é adicionado ao inventário do jogador, e o que não couber **cai no chão** aos pés dele. `type` inválido loga `reward_invalid_item` e pula aquele item.
- `commands` — executados pelo **console** (`Bukkit.dispatchCommand` com o console sender), um por linha, com `{player}` substituído pelo nome do jogador.
- Só recebem recompensa os jogadores **online e vivos** no momento em que o andar foi limpo — quem morreu naquele andar (`deadThisFloor`) fica de fora dessa rodada de recompensas (mas volta a receber nos andares seguintes, se reviver como espectador do resto da run normalmente).

#### Configuração de mobs: vanilla vs MythicMobs

Cada entrada de `floors.<n>.mobs` é **um** dos dois formatos abaixo (`type` para mob vanilla, `mythic` para MythicMobs). Se ambos os campos aparecerem na mesma entrada, `mythic` tem prioridade e `type` é ignorado — então nunca misture os dois, use um ou outro (código: [`DungeonSession.spawnFloorMobs`](src/main/java/com/eternity/infinitytower/tower/session/DungeonSession.java#L644-L720)).

**Mob vanilla** — use `type` com o nome do `org.bukkit.entity.EntityType` (maiúsculo, ex.: `ZOMBIE`, `SKELETON`, `SPIDER`, `WITHER_SKELETON`):

```yaml
floors:
  1:
    mobs:
      - type: ZOMBIE
        amount: 3
        equipment:
          hand:
            material: IRON_SWORD
            enchantments:
              sharpness: 3
              fire_aspect: 1
          helmet: IRON_HELMET
          chestplate: IRON_CHESTPLATE
          leggings: IRON_LEGGINGS
          boots: IRON_BOOTS
        spawns:
          - world: world
            x: 0.5
            y: 100.0
            z: 0.5
```

`equipment` é **opcional** e só se aplica a mobs `type` (vanilla) — o MythicMobs já tem seu próprio sistema de equipamento. Slots aceitos, todos opcionais e independentes:

| Slot | Equivalente |
|---|---|
| `hand` | Item na mão principal (ex.: uma espada) |
| `offhand` | Item na mão secundária (ex.: um escudo) |
| `helmet` | Capacete |
| `chestplate` | Peitoral |
| `leggings` | Calça |
| `boots` | Botas |

Cada slot aceita dois formatos:

- **Simples** — só o material: `hand: DIAMOND_SWORD`.
- **Com encantamentos** — um mapa com `material` e `enchantments`:
  ```yaml
  hand:
    material: DIAMOND_SWORD
    enchantments:
      sharpness: 5
      unbreaking: 3
  ```

`material` é o nome de um `Material` válido (ex.: `DIAMOND_SWORD`, `NETHERITE_CHESTPLATE`). `enchantments` é um mapa `nome_do_encantamento: nível`, onde o nome é a chave vanilla em minúsculo (ex.: `sharpness`, `protection`, `unbreaking`, `fire_aspect`, `knockback`) — os mesmos nomes usados no `/enchant` do Minecraft. Os níveis não são limitados ao máximo vanilla (equivalente a um encantamento "unsafe": dá pra colocar `sharpness: 10`, por exemplo).

Um slot com material ou encantamento inválido é ignorado individualmente (loga `dungeon.equipment_invalid_item` / `dungeon.equipment_invalid_enchantment`) sem afetar os demais. O equipamento configurado **não dropa** quando o mob morre (drop chance forçado para 0 em todos os slots usados).

**Mob do MythicMobs** — use `mythic` com o nome interno exato do mob (o mesmo definido nos arquivos de config do MythicMobs, ex.: `mythicmobs/Mobs/*.yml`):

```yaml
floors:
  2:
    mobs:
      - mythic: SkeletalKnight
        amount: 3
        spawns:
          - world: world
            x: -52.5
            y: 167.0
            z: 39.5
```

Regras e comportamento de ambos os formatos:

- `amount` — quantidade a spawnar; distribuída ciclicamente entre os pontos de `spawns` (ex.: 6 mobs com 3 spawns → 2 em cada ponto).
- `spawns` — lista de coordenadas (`world`, `x`, `y`, `z`); se vazia, o mob cai como fallback na localização de um jogador online.
- Se `type` for inválido (não existe no `EntityType`), a entrada é ignorada e loga `dungeon.vanilla_mob_invalid` no console.
- Se `mythic` for usado sem o plugin MythicMobs instalado, a entrada é ignorada e loga `dungeon.mythic_missing` no console (o spawn é feito via reflexão, então o InfinityTower nem precisa do MythicMobs no classpath para compilar).
- Um andar sem nenhum mob válido spawnado encerra a run automaticamente se `allow_empty_floors: false`.
- Em **arenas alternativas** (`<id>_arena2`, etc.), a lista `floors.<n>.mobs` da arena normalmente só reescreve `spawns` (mesma ordem/índice da lista raiz) — `type`/`mythic`/`amount` continuam vindo da definição raiz.

#### Arenas alternativas e dungeons extra

Um mesmo arquivo `dungeons/<id>.yml` pode conter mais conteúdo além da dungeon principal, através de seções top-level (mesmo nível de `display`/`floors`/etc.):

**Arenas alternativas** — seção `<dungeonId>_arena2` (`_arena3`, `_arena4`, ...). Pense nisso como "a mesma dungeon, mas rodando em outro lugar do mapa" (útil pra ter N instâncias físicas simultâneas sem duplicar todo o YAML e sem duplicar o histórico/ranking, que continua contando pro `dungeonId` original). A arena sobrescreve **só o que for informado** por cima da configuração raiz — na prática, isso costuma ser:

```yaml
solo_10_arena2:
  player_spawns: [...]
  return_spawn: {...}
  party_leader_spawn: {...}      # se for dungeon PARTY
  party_member_spawns: [...]     # se for dungeon PARTY
  floors:
    1:
      mobs:
        - spawns: [...]         # só "spawns" — type/mythic/amount continuam vindo da raiz
    2:
      mobs:
        - spawns: [...]
```

- Em `floors.<n>.mobs` da arena, cada entrada é combinada **pelo índice** com a lista raiz (a 1ª entrada da arena sobrescreve o `spawns` da 1ª entrada da raiz, e assim por diante) — só `spawns` é lido da arena, `type`/`mythic`/`amount`/`equipment` sempre vêm da definição raiz do andar.
- `/tower admin setup <dungeonId> <arena>` (ex.: `arena2` ou só `2`) abre a sessão de setup apontando pra essa seção, então `setspawn`/`addspawn`/`setreturn`/`mobspawn` editam a arena, não a raiz.
- Se a seção da arena não existir ainda no arquivo, `/tower admin setup <dungeonId> <arena>` cria ela vazia na hora de salvar.

**Dungeons extra** — qualquer outra seção top-level (que não siga o padrão `..._arenaN`) contendo pelo menos uma destas chaves: `player_spawns`, `return_spawn`, `floors`, `mode` ou `display`. Vira uma dungeon **completamente independente** (com seu próprio `dungeonId` = nome da seção, seu próprio ranking, sua própria entrada em `/tower admin list`), mas reaproveitando como base tudo que não for sobrescrito na seção. Exemplo prático: uma versão "hard" da mesma torre, no mesmo arquivo:

```yaml
# solo_10.yml
display: "&bTorre Solo"
mode: SOLO
floors: {...}
# ...

solo_10_hard:
  display: "&cTorre Solo (Difícil)"
  floors:
    1:
      mobs:
        - type: ZOMBIE
          amount: 10          # muito mais mobs que a versão normal
          spawns: [...]
```

Diferente da arena, aqui você normalmente quer sobrescrever `floors` inteiro (não só `spawns`), já que é uma dungeon com identidade própria, não uma cópia física da mesma.

## Setup de arenas (admin)

Fluxo típico para configurar uma dungeon dentro do jogo, sem editar YAML manualmente:

```
/tower admin create minha_dungeon
/tower admin setup minha_dungeon
/tower admin setspawn solo            # (ou: setspawn leader, para PARTY)
/tower admin addspawn members         # repita para cada spawn de membro (modo PARTY)
/tower admin setreturn
/tower admin mobspawn set 1           # define os pontos de spawn de mob do andar 1
/tower admin mobspawn add 1           # adiciona mais um ponto ao mesmo andar
/tower admin save
```

- `/tower admin where` mostra em qual dungeon/arena você está editando.
- `/tower admin cancel` descarta as mudanças da sessão atual.
- `mobspawn` exige que o andar já tenha ao menos um mob configurado no YAML (o comando só edita a lista `spawns`, não cria o mob em si).
- Para editar uma arena alternativa: `/tower admin setup minha_dungeon arena2` (ou apenas `2`).

## Sistema de party

- Tamanho máximo: 4 jogadores (fixo em código).
- Convites expiram após `party.invite_expire_seconds` (config.yml, mínimo 20s, padrão 20s).
- Desconexão do líder desfaz a party; desconexão de membro o remove (e desfaz se sobrar só 1).
- Ao sair um líder sem desfazer a party, a liderança passa automaticamente para outro membro.
- `PartyFriendlyFireListener` evita dano entre membros da mesma party.

## Banco de dados

Configurado em `config.yml: database`:

```yaml
database:
  enabled: true
  type: sqlite          # sqlite ou mysql
  sqlite:
    file: "database.db"
  mysql:
    host: "localhost"
    port: 3306
    database: "infinitytower"
    user: "root"
    password: ""
    useSSL: false
```

Usado para:
- **`PlayerStatsRepository`** — estatísticas agregadas por jogador (runs/wins/losses solo e party) — alimenta os placeholders de stats e o menu de estatísticas.
- **`TowerStatsRepository`** — ranking de vitórias e melhores recordes (individual/party) por dungeon.
- **`RunHistoryRepository`** / **`RunLogger`** — histórico/log de runs e uso de chaves.

O schema é criado/migrado automaticamente ao conectar (colunas ausentes são adicionadas em runtime).

## Build

```bash
mvn clean package
```

Gera `target/InfinityTower-1.0.jar` (shaded, já com SQLite/MySQL embutidos).

## Licença

Projeto interno — sem licença pública definida.
