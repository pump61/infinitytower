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
| [EssentialsX](https://essentialsx.net/) | soft-depend | `/back` ao sair da dungeon (`EssentialsBackUtil`) |
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
| `/tower top <wins\|time> <dungeonId> [limit]` | Ranking de vitórias ou melhor tempo (limite 1–50, padrão 10) |
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
| `%infinitytower_best_player_<dungeonId>_name%` | Nome do melhor tempo individual da dungeon |
| `%infinitytower_best_player_<dungeonId>_time_ms%` | Melhor tempo individual em milissegundos |
| `%infinitytower_best_party_<dungeonId>_leader%` | Líder da melhor run em party |
| `%infinitytower_best_party_<dungeonId>_time_ms%` | Melhor tempo em party (ms) |

> Exemplo: `%infinitytower_top_wins_1_solo_10_name%` — nome do #1 do ranking de vitórias da dungeon `solo_10`.
> Posições sem registro retornam `-` (texto) ou `0` (valor numérico).

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
- `main-menu` / `stats-menu` — variante alternativa de menu (o layout "oficial" usado em runtime é `menus.yml`; estas seções ficam como referência/legado).
- `spawn` — spawn global de fallback quando uma dungeon não tem `return_spawn` configurado.
- `database.*` — ver seção [Banco de dados](#banco-de-dados).
- `database.enter_teleport_delay_seconds` — delay (segundos) antes de teleportar o jogador para a arena ao entrar.
- `debug.enabled` — liga logs extras de depuração.

### `menus.yml`

Controla três telas:

- `main_menu` — botões **Solo** (slot 12) e **Party** (slot 14) que abrem os respectivos submenus.
- `player_stats_menu` — cards de estatística Solo/Party com placeholders `{player} {soloRuns} {soloWins} {soloLosses} {partyRuns} {partyWins} {partyLosses}`.
- `menus.solo` / `menus.party` — telas que listam as dungeons daquele modo nos slots definidos em `dungeon-slots`, usando o template `dungeon-item` com placeholders `{id} {display} {mode} {max_floors}`.

Cada item aceita `material`, `name`, `lore`, `custom_model_data` e (nos menus de dungeon) `hide_attributes`. `filler` preenche os slots vazios do inventário.

### `lang/pt-BR.yml`

Todas as mensagens do plugin, organizadas em seções: `titles` (titles de tela), `messages` (mensagens gerais), `party` (sistema de party), `usage` (mensagens de uso incorreto), `help` (texto de `/tower help`), `database`, `setup` e `logs` (mensagens de console). Edite livremente — os placeholders `{...}` de cada linha são substituídos pelo código na hora do envio.

### Dungeons (`dungeons/<id>.yml`)

Cada arquivo YAML descreve uma dungeon. Chaves principais:

| Chave | Descrição |
|---|---|
| `display` | Nome exibido (aceita cor `&`/hex `&#RRGGBB`) |
| `max_floors` | Quantidade de andares |
| `mode` | `SOLO` ou `PARTY` |
| `allow_solo_in_party` | Permite iniciar dungeon SOLO estando na PARTY |
| `next_floor_delay_seconds` | Delay entre andares |
| `start_delay_seconds` | Delay ao entrar antes do 1º andar |
| `allow_empty_floors` | Se `false`, andar sem mob configurado cancela a run |
| `access.require_key` / `access.consume_key` | Exige e/ou consome chave para entrar |
| `access.key.*` | Aparência do item-chave (`material`, `name`, `lore`, `custom-model-data`, `glow`) — placeholders `{id} {display} {max_floors}` |
| `access.limiter.enabled` / `cooldown_seconds` / `max_entries_per_day` | Cooldown e limite diário de entradas por jogador |
| `menu-item.*` | Aparência do item representando a dungeon no menu |
| `player_spawns` | Lista de spawns (modo solo, um por tentativa) |
| `party_leader_spawn` / `party_member_spawns` | Spawns específicos do modo party |
| `return_spawn` | Local de retorno ao concluir/sair (cai no `spawn` global do `config.yml` se ausente) |
| `titles.preparing` / `on_enter` / `floor_cleared` / `completed` | Titles específicos da dungeon (placeholders `{delay} {floor} {dungeon}`) |
| `floors.<n>.mobs` | Lista de mobs do andar `n` — ver [Configuração de mobs](#configuração-de-mobs-vanilla-vs-mythicmobs) abaixo |
| `floors.<n>.rewards` | `enabled`, `items` (lista `type`/`amount`) e/ou `commands` (executados como console, com `{player}`) |

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
          hand: IRON_SWORD
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

O valor de cada slot é o nome de um `Material` válido (ex.: `DIAMOND_SWORD`, `NETHERITE_CHESTPLATE`). Um slot com material inválido é ignorado (loga `dungeon.equipment_invalid_item`) sem afetar os demais. O equipamento configurado **não dropa** quando o mob morre (drop chance forçado para 0 em todos os slots usados).

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

**Arenas alternativas:** dentro do mesmo arquivo, uma seção top-level `<dungeonId>_arena2` (ou `arena3`, etc.) sobrescreve apenas os campos informados (tipicamente spawns e `floors.<n>.mobs[].spawns`) por cima da configuração raiz — útil para ter várias instâncias físicas da mesma dungeon sem duplicar todo o YAML. Use `/tower admin setup <dungeonId> <arena>` para editar uma arena específica.

**Dungeons "extra" no mesmo arquivo:** uma seção top-level que não seja `..._arenaN` e que contenha `player_spawns`/`return_spawn`/`floors`/`mode`/`display` é registrada como uma dungeon independente (id = nome da seção), reaproveitando o resto do arquivo como base.

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
