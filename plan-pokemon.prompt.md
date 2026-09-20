# Plano — Integração PKHeX + OpenHome no SwtFrontend

> **Prompt executável para agente de código (Codex / Boulder / Sisyphus).**
> Cole este arquivo como contexto principal ao iniciar a task `pokemon-tools`.
> Objetivo: portar **todas** as funcionalidades do [PKHeX](https://github.com/kwsch/PKHeX) e do [OpenHome](https://github.com/andrewbenington/OpenHome) como **tela adicional nativa** dentro do SwtFrontend, acessível pela **Home** via **botão circular com ícone de Pokébola** (estética Switch), integrada às **coleções de ROMs + saves** que o usuário já possui.

---

## 1) Prompt de Execução (cole no agente)

```
Você é um agente senior Android (Kotlin, Views + Compose interop, NDK).
Sua missão: implementar "Pokémon Tools" no projeto SwtFrontend (/mnt/GIT/SwtFrontend).

Contexto do projeto:
- App Android launcher estilo Nintendo Switch (Views/XML, não Compose-first).
  Home = LibraryActivity (home row + grid 2x2 + dock 5 botões). Ver:
  app/src/main/java/br/com/redclaw/swt/views/LibraryActivity.kt
  app/src/main/java/br/com/redclaw/swt/views/adapters/DockAdapter.kt
  app/src/main/java/br/com/redclaw/swt/views/adapters/HomeGamesAdapter.kt
  app/src/main/java/br/com/redclaw/swt/views/GameGridActivity.kt
  Namespace: br.com.redclaw.swt | GPLv3 | minSdk 24, targetSdk 35, Kotlin 2.x, AGP 8.11.x, JDK 17.
- Biblioteca de ROMs = :retrograde-app-shared (Lemuroid vendored):
  GameSystem, SystemID, CoreID, Game (Room entity: id, fileUri, systemId, title),
  StorageProviderRegistry, DirectoriesManager (getSystemDirectory(), getSavesDirectory()),
  SavesManager, StatesManager, StorageFilesMerger.
  Saves em: filesDir/saves/ e filesDir/states/
- Cores libretro baixados em runtime via CoreUpdaterImpl (filesDir/cores/<version>/<abi>/core.so)
  GameLoaderHelper já resolve gameFile e core path.
- UI usa AccentManager/ThemeManager (12 accents Switch) + RetroArch monochrome icons + Coil.

Origem das funcionalidades a portar:
- PKHeX (C# .NET, repo kwsch/PKHeX) → PKHeX.Core: edição completa de Pokémon (PKM),
  boxes, OT/TID/SID, IV/EV, moves, legality checker, import/export .pk* / .pb8, etc.
- OpenHome (Flutter/Dart, repo andrewbenington/OpenHome) → UI/UX tipo Pokémon HOME:
  boxes visuais, living dex, wonder box, GTS-like, save manager, integração PKHeX.

Requisito de produto:
- Nova feature "Pokémon Tools" acessível por botão circular Pokébola na Home.
- Deve listar automaticamente saves compatíveis detectados nas coleções de ROMs do usuário
  e permitir abrir/editar como PKHeX/OpenHome fariam, salvando de volta com checksum correto.
- Portar TODAS as funcionalidades (não apenas viewer). Ver checklist na seção 4 e 5 deste plano.
- **Integração unificada obrigatória:** OpenHome e PKHeX NÃO podem aparecer como dois modos/apps
  separados dentro do SwtFrontend. O usuário deve perceber UM único app coeso:
  o Box Viewer / Transfer Hub / Dex do OpenHome e o Editor / Legality do PKHeX são
  partes do mesmo grafo de navegação, mesmo design system (Switch + Material3), mesma
  toolbar/bottom bar, mesmas animações e mesmos componentes Compose. Navegação é
  contínua: tocar num Pokémon na box (OpenHome) abre direto o editor (PKHeX) sem
  trocar de "app" ou tema.
- PKHeX.Core deve ser reimplementado em Kotlin (JVM) puro, sem .NET runtime, para evitar dependências externas e manter APK enxuto.
- OpenHome UI deve ser portado mantendo a estética Switch e integração com o launcher.

Restrições:
- Sem `as any` / `@ts-ignore`. Código Kotlin strict, null-safety.
- **Todas as views novas (Pokémon Tools) devem ser 100% Jetpack Compose.**
  `LibraryActivity` (Views) continua como está, mas `PokemonActivity` e TODAS as telas
  filhas (SaveSelector, BoxViewer, PkmEditor, TransferHub, Dex, etc) são Compose puro
  via `ComponentActivity.setContent { }`. Não criar XML/layout novo para a feature.
  Interop Views→Compose apenas no ponto de entrada (Intent LibraryActivity → PokemonActivity).
- Manter GPLv3 headers. Não commitar sem pedido.
- Ao final: lsp_diagnostics limpo nos arquivos alterados + ./gradlew :app:assembleDebug ok.

Entregue de forma incremental por fases (ver seção 7). Comece pela Fase 0 e 1.
```

---

## 2) Visão Geral

### 2.1 O que é PKHeX

- **PKHeX**: editor de saves Pokémon escrito em C# (.NET 8/9). `PKHeX.Core` é a biblioteca pura que implementa:
  - Formatos `PKM` (PK1..PK9, PB7, PB8, PA8) — estrutura binária de ~260-376 bytes por mon.
  - Formatos de save: `SAV` (Gen1-7), `SAV` + `SAV2`, `SAV` Gen8/9 (Switch: `main` de  ~0.5-1MB).
  - Criptografia/embaralhamento: `BlockShiny`, `BlockData`, `SaveBlock` (Gen8+).
  - Checksums: CRC16-CCITT, SHA256 (Gen8+), `SaveFile` → `ChecksumsValid`.
  - Legality checker (`LegalityAnalysis`), move/ability validation, encounter DB.
  - Import/export: `.pk1` … `.pk9`, `.pb8`, `.ck3`, `.sav`, `.bin`, QR, showdown.
- **Licença**: GPLv3 (compatível com SwtFrontend).

### 2.2 O que é OpenHome

- **OpenHome**: app Flutter (Android/iOS/Desktop) que é um "Pokémon HOME offline":
  - UI de **Boxes** (32 boxes x 30 slots, sprites, drag-and-drop, filtros, ordenação).
  - **Pokédex vivo**, **Party**, **Daycare**, **Fusões**, **Pokédex config**.
  - **Save manager**: abre saves de Switch (SWSH, BDSP, PLA, SV) + 3DS + DS/GBA via `PKHeX.Core` compilado para Dart FFI.
  - **Transferência entre saves** (arrasta Pokémon de um save para outro — Home-like).
  - **Editor PKHeX integrado**: tapping no Pokémon abre editor completo (stats, moves, ribbons, etc).
  - **Backup/restore**, **legality badge** (legal/ilegal/hacked).
- **Licença**: GPLv3. Stack: `flutter`, `dart:ffi` → `PKHeX.Core` via `dotnet` nativo.

### 2.3 Objetivo no SwtFrontend

Unificar os dois dentro do launcher como **uma única feature coesa**, sem sair do app, usando diretamente os saves que já estão no storage do SwtFrontend (saves de emuladores libretro + saves nativos).

> **Princípio de integração:** Pokémon Tools é UM app, não dois. O usuário não deve
> conseguir dizer onde termina OpenHome e onde começa PKHeX. O Box Viewer (OpenHome)
> e o Editor/Legality (PKHeX) compartilham: mesmo `NavHost` Compose, mesmo `ViewModel`
> de save, mesmo `Scaffold`/`TopAppBar`/`NavigationBar`, mesma paleta Switch
> (AccentManager bridge → `MaterialTheme.colorScheme`), mesmos componentes
> (`PokemonSprite`, `BoxGrid`, `LegalityBadge`). O fluxo é contínuo:
> `SaveSelector → BoxViewer → (tap) → PkmEditor → (back) → BoxViewer` sem troca de contexto.

```
[LibraryActivity — HOME Switch]  (Views — existente, NÃO migrar)
  ├── Home Row (jogos recentes)
  ├── Grid 2x2 (Coleções | Todos os Jogos | Apps | Dashboard)
  └── Dock (5 botões atuais)  +  [NOVO] botão circular Pokébola
                                        │  Intent
                                        ▼
                              PokemonActivity  (Compose — 100% da feature nova)
                              └─ PokemonNavGraph (Compose NavHost, single graph)
                                   ├── SaveSelectorScreen        [OpenHome: save manager]
                                   ├── BoxViewerScreen           [OpenHome: boxes + party]
                                   │     └─ (tap) → PkmEditorScreen  [PKHeX: editor completo]
                                   │              └─ LegalitySheet   [PKHeX: LegalityAnalysis]
                                   ├── TransferHubScreen         [OpenHome: A↔B + PKHeX: convert]
                                   ├── PokedexScreen             [OpenHome: living dex]
                                   ├── DaycareScreen             [OpenHome]
                                   └── Settings/BackupScreen     [compartilhado]
                                   Todos os screens = @Composable, mesmo design system.
```

---

## 3) Ponto de Integração na UI (Home)

### 3.1 Local exato

`LibraryActivity.setupDock()` hoje cria 5 `DockItem` (Jogos, Apps, Conquistas, Perfil, Config).
O **botão Pokébola** NÃO vai no dock — vai como **botão circular flutuante central** acima do dock, ou como **6º item do dock** com destaque, ou como **FAB circular no centro da Home** (estilo botão HOME do Switch).

**Decisão recomendada (Switch fiel):**

- Adicionar **botão circular central destacado** entre o `cardsGrid` e o `dockContainer`, no `activity_library.xml`:
  - `FrameLayout` 72dp, `elevation 8dp`, `background = @drawable/bg_pokeball_button` (círculo vermelho/branco com centro).
  - Ícone: `ic_pokeball` (vetor monochrome, tint = branco). Ao focar (D-pad), `border = AccentManager.createRoundFocusBorder()` + scale 1.08x + haptic.
  - No `LibraryActivity.onCreate()`, após `setupDock()`, chamar `setupPokemonButton()` que faz `startActivity(Intent(this, PokemonActivity::class.java))`.
  - Alternativa fallback: 6º `DockItem` com `R.drawable.ic_pokeball` e `R.color.pokeball_red` se layout não permitir FAB.

### 3.2 Navegação

```kotlin
// LibraryActivity.kt
private fun setupPokemonButton() {
    val pokeBtn: View = findViewById(R.id.library_poke_button)
    pokeBtn.background = AccentManager.createRoundFocusBorder(this) // ou bg_pokeball
    pokeBtn.setOnClickListener {
        startActivity(Intent(this, PokemonActivity::class.java))
    }
    // D-pad: nextFocusDown = pokeBtn, pokeBtn nextFocusUp = homeRow
}
```

- `PokemonActivity` estende `ComponentActivity` (ou `ScaledAppCompatActivity` com `setContent`) e é **100% Compose**: `setContent { SwtPokemonTheme { PokemonNavGraph() } }`. Nenhum XML novo para a feature.
- Compartilhar tema: bridge `AccentManager.getAccentColor()` → `MaterialTheme.colorScheme.primary` para que Pokémon Tools respeite os 12 accents Switch do app host.
- Navegação única: `androidx.navigation:navigation-compose` com um único `NavHost` (`pokemon_nav_graph`). OpenHome e PKHeX são destinos do mesmo grafo, não grafos separados.

### 3.3 Strings / i18n

Adicionar em `res/values/strings.xml` (pt-BR default, en, es):
```
pokemon_tools_title = "Pokémon Tools"
pokemon_tools_desc  = "Gerencie saves e Pokémon das suas ROMs"
dock_pokemon        = "Pokémon"
```

---

## 4) Funcionalidades a Portar — Checklist Completo

### 4.1 PKHeX.Core (obrigatório — 100%)

| Grupo | Funcionalidades | Prioridade |
|-------|----------------|------------|
| **Save I/O** | Detectar formato (SAV1..SAV9, MAIN SWSH/BDSP/PLA/SV), ler/escrever com checksum, backup .bak, detecção de tamanho/corrupção | P0 |
| **PKM I/O** | Ler/escrever PK1..PK9/PB7/PB8/PA8, conversão entre gerações, import/export `.pk*`, `.pb8`, Showdown, QR | P0 |
| **Box/Party** | Ler boxes (30x32), party (6), daycare, battle box, trade, fundo, ordenação | P0 |
| **Editor — Info** | Espécie, forma, nível, EXP, nature, gender, shiny, PID/EC, language, version | P0 |
| **Editor — OT** | OT name, TID, SID, OT gender, handling trainer, friendship, affection | P0 |
| **Editor — Stats** | IV (6x 0-31), EV (6x 0-252/510 total), CP (GO), Dynamax/Gigantamax, AV (LGPE), hyper training | P0 |
| **Editor — Moves** | 4 moves, PP, PP Ups, relearn moves, TR/TR, encounter moves validação | P0 |
| **Editor — Misc** | Ability, held item, ball, form, met location/date/level, fateful encounter, pokerus | P0 |
| **Editor — Ribbons/Marks** | Ribbons (40+), marks, contest, memories | P1 |
| **Legality** | `LegalityAnalysis` por Pokémon + por save inteiro, badges (✓/⚠/✗), sugestão de correção | P0 |
| **Utilitários** | Randomizer, batch editor, mass release, dump boxes, wondercard/event import | P1 |
| **Gen8+ blocks** | `SaveBlock` editing (SWSH/SV block crypto), raid den, trainer card | P1 |

### 4.2 OpenHome (obrigatório — 100%)

| Grupo | Funcionalidades | Prioridade |
|-------|----------------|------------|
| **Box Viewer** | Grade 5x6 por box, 32 boxes, sprites animados, drag-and-drop, multi-select, filtros (tipo, shiny, egg, gen, mark), ordenação, busca por nome | P0 |
| **Sprite Layer** | Sprites `pokesprite` / `PKHeX Resources` — shiny, forma, gênero, egg | P0 |
| **Save Selector** | Lista de saves detectados (ver seção 6), preview (trainer name, dex count, play time, badges) | P0 |
| **Transfer Hub** | Arrastar Pokémon entre saves/boxes abertos (SAV A ↔ SAV B), validação de compatibilidade de geração, evolução automática se necessário | P0 |
| **Pokédex** | Living dex view (por gen, faltantes destacados, shiny dex) | P1 |
| **Party/Daycare** | Visualização party 6, daycare 2, edição inline | P1 |
| **Wonder Box / GTS mock** | Queue local de wonder trade (random entre boxes) — opcional offline | P2 |
| **Backup manager** | Auto-backup antes de salvar, restore, diff viewer | P0 |
| **Settings** | Legality filter (hide hacked), sprite source, language, box wallpaper | P1 |

---

## 5) Arquitetura Técnica — Estratégia Unificada

> **Decisão travada:** toda a feature Pokémon Tools é **Kotlin + Compose puro**,
> com **um único design system e um único NavHost**. OpenHome e PKHeX são
> fundidos — não existem "modo OpenHome" e "modo PKHeX".

### 5.1 Estratégia A — **Kotlin Native Port UNIFICADO (RECOMENDADA — ÚNICA VÁLIDA)**

> Reescrever a lógica de `PKHeX.Core` em Kotlin (JVM) + UI Compose que funde
> o Box Viewer/Transfer do OpenHome com o Editor/Legality do PKHeX no mesmo grafo.
> Não embutir .NET runtime nem Flutter engine.

**Princípio de fusão:**
- **Core único:** `:pokemon-core` (Kotlin puro) implementa `SaveFile`, `PKM`, `Box`, `Legality` — serve tanto o BoxViewer (OpenHome) quanto o Editor (PKHeX). Não há duplicação.
- **UI única:** `:pokemon-ui` (ou `app/pokemon/ui/`), 100% `@Composable`, com design system Switch compartilhado. Componentes como `PokemonSprite`, `BoxGrid`, `LegalityBadge`, `PkmForm` são usados por ambos os fluxos.
- **Estado único:** `PokemonSaveViewModel` (por save aberto) expõe `boxes: StateFlow<List<PKM?>>`, `selectedPkm: StateFlow<PKM?>`, `legality: StateFlow<LegalityAnalysis?>` — BoxViewer e Editor observam o mesmo estado. Editar no Editor reflete instantaneamente na Box.
- **Navegação única:** `PokemonNavGraph` com `NavHost(startDestination = SaveSelector)`. Transição Box→Editor é `navController.navigate("editor/${box}/${slot}")` dentro do mesmo graph, com `slideIn` (não troca de Activity).

**Prós:** sem runtime extra, APK menor, debug puro Kotlin, UI 100% Compose coesa, GPL OK, sem FFI, sem divergência Flutter/Kotlin.
**Contras:** trabalho de port (mas PKHeX.Core é bem modular; dá para portar incremental por save gen).
**Como:** criar módulo `:pokemon-core` (Kotlin puro) que implementa `SaveFile`, `PKM`, `Box`, `Legality` em Kotlin, baseado no source C# (ler `PKHeX.Core` como spec). Usar `PKHeX.Core` como referência/test oracle (comparar outputs byte-a-byte).

**Módulos:**
```
:pokemon-core        # Kotlin puro — parsing de SAV/PKM, sem Android, sem Compose
                     #  Serve OpenHome (boxes) E PKHeX (editor/legality) — core único
:pokemon-ui          # Compose puro — BoxViewer, Editor, Transfer, Dex (unificados)
                     #  Design system único (Switch + Material3), componentes compartilhados
:app                # PokemonActivity host (ComponentActivity) + PokemonNavGraph + Storage integration
                     #  Bridge AccentManager → MaterialTheme, Intent da Home
```

### 5.2 Estratégia B — Flutter Module (REJEITADA)

> Embutir OpenHome como `flutter module` dentro do SwtFrontend (Flutter add-to-app).

**Rejeitada:** viola as duas restrições novas — criaria dois apps visuais distintos (Flutter vs Compose) e não seria 100% Compose. Aumenta APK em ~20MB, precisa Flutter engine, navegação híbrida, interop SAF/storage complexo.

### 5.3 Estratégia C — .NET Embedded (REJEITADA)

> Compilar `PKHeX.Core` para Android via `.NET 8 Android` (ou NativeAOT) e expor via JNI.

**Rejeitada:** embute Mono/.NET runtime (~15-30MB), JNI verboso, e não resolve a fusão de UI — ainda precisaria reescrever tudo em Compose de qualquer forma.

**Decisão:** **A unificada é a única estratégia válida** dadas as restrições. Começar com **Gen1-7 SAV + PKM** (mais simples, sem Block crypto), depois **Gen8/9 MAIN** (Block handling).

---

## 6) Integração com Coleções de ROMs e Saves

### 6.1 Onde estão os saves hoje

| Fonte | Path | Formato |
|-------|------|---------|
| Saves libretro (battery) | `DirectoriesManager.getSavesDirectory()` → `filesDir/saves/` | `.sav` / `.srm` por `GameSystem` + `.main` para Switch |
| Save States | `DirectoriesManager.getSavesDirectory()` / `.../states/` | `.state` (NÃO editar — ignorar) |
| Storage SAF (user) | `StorageProviderRegistry` → SAF roots escolhidos em Settings | `.sav` em qualquer pasta apontada |
| Legacy Lemuroid saves | `SavesCoherencyEngine`, `MelonDsSavesMigrator`, `DesmumeMigrationHandler` | `.sav`, `melonDS.sav`, `desmume.dsv` |

### 6.2 Descoberta automática (Save Discovery)

Criar `PokemonSaveRepository`:

```kotlin
class PokemonSaveRepository(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
    private val gameDao: GameDao, // Room: listar Game instalados
) {
    data class DetectedSave(
        val game: Game?,           // null se órfão
        val saveFile: File,        // File real
        val systemId: SystemID,    // GB, GBA, NDS, 3DS, SWITCH
        val saveType: SaveType,    // SAV1..SAV9, MAIN_SWSH, MAIN_BDSP, MAIN_PLA, MAIN_SV
        val trainerName: String?,
        val isValid: Boolean,
        val sizeBytes: Long,
    )

    suspend fun scan(): List<DetectedSave>
    // 1) Enumera savesDir + cada SAF root (via StorageProviderRegistry.getStorageFiles())
    // 2) Filtra por extensão: .sav, .srm, .dsv, .dat, "main" (sem extensão, Switch)
    // 3) Tenta SaveFile.getVariantSAV(bytes) / SaveUtil.getVariantSAV()
    // 4) Para Switch MAIN: detecta tamanho (SWSH 0x7C000, BDSP 0x100000, PLA ~0xA0000, SV ~0x100000)
    // 5) Cruza com GameDao.getAll() para achar o Game dono (por systemId + nome aproximado)
}
```

**Mapeamento SystemID → PKHeX SaveType:**

| SystemID (Lemuroid) | Exemplos Game FRLG/Emerald | SaveType PKHeX | Extensão | Tamanho |
|----------------------|----------------------------|----------------|----------|---------|
| GB                   | Red/Blue/Yellow            | SAV1           | .sav     | 8KB/32KB |
| GBC                  | Gold/Silver/Crystal        | SAV2           | .sav     | 32KB |
| GBA                  | Ruby/Sapphire/Emerald/FRLG | SAV3           | .sav .srm| 128KB |
| NDS                  | DPPt/HGSS/BW/B2W2           | SAV4/5         | .sav .dsv| 256KB-8MB |
| 3DS (CITRA)           | XY/ORAS/SM/USUM            | SAV6/7         | main, .sav| 0x100000 |
| SWITCH               | SWSH/BDSP/PLA/SV           | SAV8/9         | main     | variável |

### 6.3 Fluxo de edição seguro

```
Scan → DetectedSave list → User taps save → PokemonSaveLoader.load(bytes)
  → SaveFile (boxes/party) → BoxViewer (Compose LazyGrid)
  → Tap Pokémon → PkmEditorScreen (PKHeX editor)
  → Edit → LegalityAnalysis.validate() → Badge
  → Save → SaveFile.write() → recalcula checksum/BLOCK crypto
         → backup: saveFile.renameTo(saveFile.bak_<timestamp>)
         → saveFile.writeBytes(newBytes)
         → Notifica SavesManager (coerência)
```

**Regra de ouro:** nunca sobrescrever sem backup. Todo `save()` gera `.bak_<epoch>`.

### 6.4 Integração com GameSystem existente

- Reuso de `GameSystem.findById(systemId)` para agrupar saves por sistema.
- Filtros na `PokemonActivity`: "Mostrar apenas saves com Pokémon" (usa `SaveType.isPokemonSave`).
- Ao listar saves órfãos (sem Game), permitir "Vincular a ROM" manualmente.

---

## 7) Roadmap por Fases (com critérios de pronto)

### Fase 0 — Fundação + Botão Pokébola + Shell Compose Unificado (1-2 dias)

- [ ] Criar `res/drawable/ic_pokeball.xml` (vetor) + `bg_pokeball_button.xml` (círculo).
- [ ] Editar `res/layout/activity_library.xml`: adicionar `library_poke_button` (FAB circular) entre `cardsGrid` e `dock`.
- [ ] Editar `LibraryActivity.kt`: `setupPokemonButton()` + `startActivity(Intent(this, PokemonActivity::class.java))`.
- [ ] Criar `PokemonActivity.kt` como `ComponentActivity` 100% Compose: `setContent { SwtPokemonTheme(AccentManager.getAccentColor()) { PokemonNavGraph() } }` com placeholder "Pokémon Tools — em breve" (Compose `Text` + `Scaffold`).
- [ ] Criar `PokemonNavGraph.kt` (Compose `NavHost` com `rememberNavController()`, `startDestination = "save_selector"`, destinos stub: `save_selector`, `box_viewer`, `editor`, `transfer`, `dex`).
- [ ] Criar módulos Gradle: `:pokemon-core` (Kotlin lib) + `:pokemon-ui` (Compose) — ou tudo em `:app/pokemon/` se preferir monólito inicial. Configurar `composeOptions`, `buildFeatures.compose = true`, `navigation-compose`, `material3`.
- [ ] Adicionar strings i18n (pt-BR/en/es) + `strings.xml`.
- [ ] Definir `SwtPokemonTheme` (bridge `AccentManager` → `MaterialTheme.colorScheme`) e componentes base compartilhados (`PokemonScaffold`, `PokemonTopBar`) — usados por TODAS as telas.

**Pronto:** botão aparece na Home, abre `PokemonActivity` Compose com NavHost vazio, `assembleDebug` passa. Nenhum XML novo além do botão da Home.

### Fase 1 — Save Discovery + Save I/O Gen1-3 (3-5 dias)

- [ ] Criar `:pokemon-core`:
  - `SaveType`, `SaveFile` (sealed), `PKM` (sealed PK1..PK9), `ChecksumUtil` (CRC16).
  - `SaveDetector.getVariantSAV(bytes: ByteArray): SaveType?`
  - Implementar `SAV3` (GBA Emerald/FRLG) primeiro — mais testado, mais ROMs no acervo.
  - Implementar `SAV1`/`SAV2` (GB/GBC) em seguida.
  - Testes unitários com fixtures `.sav` reais (anonimizados) — comparar byte-a-byte com PKHeX export.
- [ ] Criar `PokemonSaveRepository` + `PokemonSaveLoader` em `:app` (ou `:pokemon-core` para scan).
- [ ] UI `SaveSelectorScreen` **Compose** (`LazyColumn`, `PullToRefreshBox`, `EmptyState`, `SaveCard` com trainer sprite + badges) — já usando `SwtPokemonTheme`.
- [ ] Ao tocar save → `BoxViewerScreen` read-only (mostrar boxes sem edição ainda) — navegação via `navController.navigate("box/${saveId}")` no mesmo `PokemonNavGraph`.

**Pronto:** usuário vê seus saves reais listados; abrir um save mostra boxes/party (somente leitura).

### Fase 2 — BoxViewer Completo (estilo OpenHome) — Compose (4-6 dias)

- [ ] Portar box viewer do OpenHome para **Compose puro** (mesmo `SwtPokemonTheme` da Fase 0):
  - `BoxGrid` (`LazyVerticalGrid` 5x6 = 30 slots), `BoxTabs` (`ScrollableTabRow` Box 1..32), `BoxWallpaper`.
  - `PokemonSprite` (`@Composable`, Coil `AsyncImage` com `pokesprite` / `PKHeX/Resources` — 68x56, cache) — componente compartilhado com Editor e Transfer.
  - Drag-and-drop entre slots (Compose `dragAndDrop` + `BoxState.move()` via `PokemonSaveViewModel` compartilhado).
  - Long-press → `DropdownMenu` Compose (editar, liberar, duplicar, exportar .pk* → navega para `editor` no mesmo graph).
  - Filtros: shiny, egg, gen, tipo, mark, search (`SearchBar` Compose).
- [ ] Party bar (6 slots horizontais, `LazyRow`) + Box ↔ Party drag — mesmo `PokemonSaveViewModel`.
- [ ] Integrar com `SaveFile`: `getBoxSlot(box, slot)`, `setBoxSlot(box, slot, pkm)` — estado único observado por BoxViewer e Editor.
- [ ] `LegalityBadge` (Compose `Badge`/`Icon`) já visível no sprite nesta fase (verde/vermelho/amarelo).

**Pronto:** navegação de boxes fluida, mover Pokémon entre boxes/party persiste no save (com backup).

### Fase 3 — Editor PKHeX Completo — Compose, integrado ao mesmo graph (5-8 dias)

> **Integração obrigatória:** `PkmEditorScreen` NÃO é tela separada com tema próprio.
> É destino `editor/{box}/{slot}` do mesmo `PokemonNavGraph`, usa o mesmo
> `PokemonSaveViewModel` (mesmo `SaveFile` + `PKM` em memória), mesmo `Scaffold`/
> `TopAppBar`, e ao salvar faz `viewModel.updatePkm(box, slot, edited)` → BoxViewer
> reflete instantaneamente (mesmo `StateFlow`).

- [ ] `PkmEditorScreen` **Compose** com abas (`PrimaryTabRow` + `HorizontalPager`):
  - **Main**: espécie, forma, nível, EXP, nature, gender, shiny, PID, ability, held item, language, friendship.
  - **Stats**: IV/EV (6 `Slider` Compose), hyper training, Dynamax level, AV (LGPE).
  - **Moves**: 4 moves + PP/PP Ups + relearn + TR — `ExposedDropdownMenuBox` + autocomplete.
  - **OT/Misc**: OT/TID/SID, OT gender, ball, met location/date/level, egg, fateful, pokerus.
  - **Ribbons/Memories/Marks**: `ExpandableList` Compose.
  - **Cosmetic**: nickname, markings, favorite — `TextField` Compose.
- [ ] Cada campo com validação inline + `LegalityBadge`/`LegalityHint` (mesmo componente do BoxViewer).
- [ ] Botão "Randomize" e "Set to Legal" (auto-corrige PID/IV para legal).
- [ ] Export/Import: `.pk3` etc + Showdown paste (`ShareSheet` + `ActivityResultContracts`).
- [ ] Todos os inputs são Compose (`TextField`, `Slider`, `DropdownMenu`, `FilterChip`) — zero Views.

**Pronto:** editar qualquer Pokémon e salvar volta ao save com checksum válido.

### Fase 4 — Legality Checker + Transfer Hub — Compose unificado (3-4 dias)

- [ ] Portar `LegalityAnalysis` (ou implementar subset): checa encounter, moves, IV/ability/ball, ribbons.
  - `LegalityBadge` Compose (mesmo componente de BoxViewer/Editor): `LEGAL` (verde) / `ILLEGAL` (vermelho) / `HACKED` (amarelo) no sprite.
  - `LegalitySheet` (`ModalBottomSheet` Compose) por Pokémon com lista de erros/warnings clicáveis — aberta tanto da Box quanto do Editor (mesmo componente).
- [ ] `TransferHubScreen` **Compose**: abrir 2 saves lado a lado (SAV A | SAV B) no mesmo `NavHost` (`transfer/{saveA}/{saveB}`), `Row` com dois `BoxGrid` + drag entre eles via `PokemonSaveViewModel` compartilhado.
  - Validação de compatibilidade (ex: PK3 → SAV7 precisa evoluir via `PKM.convertTo()`).
  - Log de transferências (`LazyColumn` Compose).
- [ ] Batch operations: liberar todos hacked, ordenar por dex — `FloatingActionButton` + `AlertDialog` Compose.

**Pronto:** usuário pode mover Pokémon entre saves de gerações diferentes (HOME-like).

### Fase 5 — Saves Gen4-9 + Switch MAIN (5-7 dias)

- [ ] Implementar `SAV4` (NDS DPPt/HGSS), `SAV5` (BW/B2W2), `SAV6/7` (3DS).
- [ ] Implementar `SAV8` (SWSH/BDSP/PLA) e `SAV9` (SV) — Block crypto:
  - `SaveBlock8` handling: `BlockInfo`, `K`, `SHA256`, `XorShift`.
  - Testar com fixtures MAIN reais.
- [ ] Suporte a `melonDS.sav` e `desmume.dsv` → converter para `.sav` raw antes de abrir.
- [ ] Suporte a saves Switch decryptados (usuário provê `main` já dumpado; não fazer decrypt de save criptografado no Switch — fora do escopo/emulador já entrega descriptografado).

**Pronto:** cobre 100% dos jogos Pokémon de GB até SV.

### Fase 6 — Pokédex, Daycare, Backup, Polish — Compose (2-4 dias)

- [ ] Living Dex **Compose**: `LazyVerticalGrid` dex nacional, faltantes em sombra, shiny toggle (`Switch` Compose), contagem — mesmo `PokemonSprite` compartilhado.
- [ ] Daycare viewer **Compose** (2 slots, `Row` + `Card`).
- [ ] Backup manager **Compose**: `LazyColumn` de `.bak_*`, restore, diff (quais Pokémon mudaram) — `ListItem` + `AlertDialog` Compose.
- [ ] Settings **Compose**: `PreferenceScreen` em Compose (legality filter, sprite source, box wallpaper, idioma) — `Switch`, `DropdownMenu`.
- [ ] Sons/haptics Switch-like, animações Compose (`AnimatedContent`, `animateItem`), empty states (`EmptyState` Compose), onboarding (`HorizontalPager` Compose "Como usar").

**Pronto:** feature parity total com OpenHome.

### Fase 7 — QA, Performance, Docs (1-2 dias)

- [ ] Testes instrumentados: abrir/salvar cada gen sem corromper.
- [ ] `lsp_diagnostics` limpo, `./gradlew :app:assembleDebug` + `assembleRelease` ok.
- [ ] Atualizar `README.md` + `AGENTS.md` com nova feature.
- [ ] Screenshots para PR.

---

## 8) Estrutura de Arquivos Proposta

```
app/
  src/main/java/br/com/redclaw/swt/
    pokemon/
      PokemonActivity.kt                 # ComponentActivity 100% Compose host
      PokemonNavGraph.kt                 # Compose NavHost único (saveList → box → editor → transfer → dex)
      theme/
        SwtPokemonTheme.kt               # bridge AccentManager → MaterialTheme.colorScheme
        PokemonScaffold.kt               # Scaffold compartilhado (TopBar + BottomBar)
      data/
        PokemonSaveRepository.kt
        PokemonSaveLoader.kt
        PokemonSaveViewModel.kt          # ViewModel único compartilhado (boxes + selectedPkm + legality)
        PkmRepository.kt
      ui/
        SaveSelectorScreen.kt            # @Composable — LazyColumn saves
        BoxViewerScreen.kt               # @Composable — LazyVerticalGrid boxes + party
        PkmEditorScreen.kt               # @Composable — TabRow + HorizontalPager (PKHeX)
        TransferHubScreen.kt             # @Composable — Row 2x BoxGrid
        PokedexScreen.kt                 # @Composable
        DaycareScreen.kt                 # @Composable
        BackupScreen.kt                  # @Composable
        component/
          PokemonSprite.kt               # @Composable Coil + pokesprite (compartilhado)
          BoxGrid.kt                     # @Composable
          LegalityBadge.kt               # @Composable (compartilhado Box + Editor)
          LegalitySheet.kt               # @Composable ModalBottomSheet (compartilhado)
          EmptyState.kt                  # @Composable
      util/
        ChecksumUtil.kt
        BackupManager.kt

pokemon-core/                            # módulo Kotlin puro (ou app/pokemon-core/)
  src/main/kotlin/br/com/redclaw/swt/pkm/ # SEM Compose, SEM Android — core puro
    SaveType.kt
    SaveFile.kt                          # sealed + factory
    saves/
      SAV1.kt  SAV2.kt  SAV3.kt  SAV4.kt  SAV5.kt  SAV6.kt  SAV7.kt  SAV8.kt  SAV9.kt
      SaveBlock8.kt                      # Gen8+ block handling
    pkm/
      PKM.kt  PK1.kt ... PK9.kt  PB7.kt  PB8.kt  PA8.kt
      PkmConverter.kt                    # PKM convert entre gens
    legality/
      LegalityAnalysis.kt
      EncounterDB.kt
    io/
      PkmImportExport.kt                 # .pk*, showdown
    resources/
      species.json  moves.json  items.json  abilities.json  # dados estáticos

# Nenhum layout XML novo para a feature — tudo Compose
res/
  drawable/ic_pokeball.xml               # único drawable novo (botão Home)
  drawable/bg_pokeball_button.xml
  layout/activity_library.xml            # + poke button (único XML tocado)
  values/strings.xml                     # + pokemon strings
```

> **Regra:** `app/src/main/res/layout/*` NÃO recebe layouts novos para Pokémon Tools.
> Toda tela nova é `@Composable` em `pokemon/ui/`. O único XML tocado é
> `activity_library.xml` para o botão Pokébola. Monólito inicial permitido:
> tudo em `app/src/main/java/br/com/redclaw/swt/pokemon/` sem `:pokemon-core`
> separado — extrair quando passar 250 linhas por arquivo.

---

## 9) Detalhes Técnicos Críticos

### 9.1 PKHeX.Core como spec

- Clone local para referência (não commit): `git clone https://github.com/kwsch/PKHeX.git /tmp/PKHeX`
- Arquivos-chave para portar:
  - `PKHeX.Core/PKM/PKM.cs` + `PK1.cs`..`PK9.cs`
  - `PKHeX.Core/Saves/SAV3.cs`..`SAV9.cs`, `SaveFile.cs`, `SaveUtil.cs`
  - `PKHeX.Core/Legality/LegalityAnalysis.cs`
  - `PKHeX.Core/Resources` (sprites, species names)
- Estratégia: ler C# e reescrever em Kotlin 1:1 onde possível; testes comparativos byte-a-byte.

### 9.2 Sprites

- Fonte 1: `msikma/pokesprite` (GitHub) — `pokemon-gen8/regular/*.png`, `shiny/`.
- Fonte 2: `kwsch/PKHeX` → `PKHeX.Core/Resources/sprites` (extrair).
- Empacotar em `assets/pokesprites/` ou baixar em runtime para `filesDir/pokesprites/` (cache Coil).
- Fallback: ícone genérico se sprite faltar.

### 9.3 Checksums

- Gen1-7: `CRC16-CCITT` por bloco (ver `SAV3.cs: ChecksumsValid`).
- Gen8+: `SHA256` por `SaveBlock` + `XorShift` crypto — ver `SaveBlock8.cs`.
- Sempre recalcular em `SaveFile.write()` antes de flush.

### 9.4 Compose — obrigatório para toda a feature

- **Regra dura:** `PokemonActivity` e TODAS as telas filhas são `@Composable` puro.
  `LibraryActivity` (Views) → `PokemonActivity` (Compose) via `Intent` é o ÚNICO ponto de interop.
  Não criar `Fragment`, `RecyclerView`, `ViewPager2` ou XML para Pokémon Tools.
- Tema: `SwtPokemonTheme` faz bridge `AccentManager.getAccentColor()` → `MaterialTheme.colorScheme.primary/secondary` + `Typography` Switch. Todas as telas usam `SwtPokemonTheme` — garante que BoxViewer (OpenHome) e Editor (PKHeX) pareçam o mesmo app.
- Navegação: `androidx.navigation:navigation-compose` — `NavHost` único. Destinos: `save_selector`, `box_viewer/{saveId}`, `editor/{saveId}/{box}/{slot}`, `transfer`, `dex`, `settings`. Transições `slideInHorizontally` + `fadeIn` (Material motion).
- Componentes compartilhados obrigatórios: `PokemonSprite`, `LegalityBadge`, `LegalitySheet`, `BoxGrid`, `PokemonScaffold` — usados tanto por fluxos OpenHome quanto PKHeX para garantir coesão visual.
- Preview: todo `@Composable` com `@Preview` para validação rápida sem device.

### 9.5 Storage e SAF

- Usar `StorageProviderRegistry` existente para enumerar arquivos; não reimplementar SAF.
- Para `File` vs `content://` URI: `GameLoaderHelper.resolveGameFile()` já mostra pattern de `openInputStream` → temp file.

---

## 10) Riscos e Mitigações

| Risco | Mitigação |
|-------|-----------|
| Port PKHeX incompleto/corrompe save | Sempre backup `.bak`, testes byte-a-byte com PKHeX.exe como oracle, legality check antes de salvar, flag "experimental" para Gen8+ |
| APK muito grande (sprites + dados) | Sprites não vão no APK — download sob demanda para `filesDir`; dados estáticos em JSON gzipped |
| Legalidade jurídica (Nintendo) | App não distribui ROMs/saves, só edita saves do usuário localmente; não incluir sprites proprietários no repo (baixar em runtime); aviso "use por sua conta" |
| Performance (boxes 32x30 = 960 mons) | LazyGrid, paginação por box, Coil cache, parsing lazy de PKM (só desserializa box visível) |
| Flutter vs Kotlin divergência | Se escolher estratégia A, não precisa Flutter; se fizer B como POC, manter em branch separada |
| Saves criptografados Switch | Fora do escopo — usuário deve prover `main` descriptografado (dump via JKSV/Checkpoint); documentar no onboarding |

---

## 11) Critérios de Aceite (Definition of Done)

- [ ] Botão Pokébola visível na Home e navegável por D-pad/controle.
- [ ] `PokemonSaveRepository.scan()` lista saves reais do device (pelo menos GBA/NDS testados).
- [ ] Abrir save GBA (SAV3) e ver boxes/party com sprites.
- [ ] Editar um Pokémon (trocar espécie/IV/shiny) e salvar — reabrir no app e no PKHeX desktop sem erro de checksum.
- [ ] Legality badge aparece corretamente (legal vs hacked).
- [ ] Transferir Pokémon entre dois saves (ex: Emerald → Platinum) funciona.
- [ ] Backup `.bak_*` criado a cada save.
- [ ] Nenhum `lsp_diagnostics` error nos arquivos novos/alterados.
- [ ] `./gradlew :app:assembleDebug` passa.
- [ ] README atualizado com seção "Pokémon Tools".

---

## 12) Referências

- PKHeX: https://github.com/kwsch/PKHeX (GPLv3) — `PKHeX.Core` é a lib.
- OpenHome: https://github.com/andrewbenington/OpenHome (GPLv3) — Flutter, FFI PKHeX.
- PKHeX docs: https://github.com/kwsch/PKHeX/wiki
- Pokesprite: https://github.com/msikma/pokesprite
- Lemuroid (base SwtFrontend): https://github.com/Swordfish90/Lemuroid
- SwtFrontend plano geral: `.omo/plans/swt-frontend.md`

---

## 13) Próximo Passo Imediato para o Agente

1. Criar branch `feat/pokemon-tools`.
2. Executar **Fase 0** (botão + stub activity) e abrir PR draft.
3. Em paralelo, clonar PKHeX/OpenHome em `/tmp/` e mapear `SAV3.cs` → `SAV3.kt` como spike.
4. Pedir review após Fase 0 antes de seguir para Fase 1 (evita retrabalho de navegação).

> Dica: use `task(category="deep", ...)` para Fase 0 e `task(subagent_type="explore")` para mapear PKHeX.Core antes de codar.

---

*Gerado em 2026-09-20 para SwtFrontend — SwtFrontend/plan-pokemon.prompt.md*
