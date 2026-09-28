# Plano de modularização do SwtFrontend

## 1. Objetivo

Transformar o SwtFrontend no aplicativo central para descoberta, organização e preparação de jogos, sem manter emuladores embutidos no processo do frontend. Capacidades específicas serão fornecidas por módulos instaláveis e versionados separadamente.

O primeiro módulo será o HylianBox, responsável pela execução e pelas ferramentas especializadas de Zelda para Nintendo 64. Seu código passa a viver no mesmo monorepo, em `modules/hylianbox`, mas continua sendo um APK Android independente e, ao fim da migração, sem launcher próprio.

O estado final deve ter:

- SwtFrontend como ponto único para biblioteca, catálogo, downloads, importação, patches, arquivos compactados, saves, backups, perfil RetroAchievements e instalação de módulos;
- módulos responsáveis por emulação, runtime RetroAchievements e ferramentas específicas;
- instalação/atualização sob demanda com validação de versão, hash e assinatura;
- nenhuma ROM proprietária ou credencial nos repositórios, APKs ou catálogos;
- migração gradual, testável e reversível do HylianBox standalone.

## 2. Decisões arquiteturais

### ADR-001 — Módulo como APK-companheiro

Um módulo é um APK independente, descoberto pelo SwtFrontend por contrato explícito. Não se carregará AAR, DEX ou `.so` baixado dentro do processo do host. Fora de Dynamic Features da Play Store, Android não oferece um mecanismo geral e seguro para isso.

O APK-companheiro oferece isolamento de processo e JNI, atualização atômica pelo Package Manager, validação de assinatura e consentimento do usuário. Gameplay, core, overlay e ferramentas permanecem no módulo; o APK principal poderá ficar sem cores de emulação.

### ADR-002 — Monorepo com histórico incorporado

O HylianBox será incorporado em `modules/hylianbox` por merge de históricos não relacionados e importação com prefixo. Não será usado submodule: apagar `zonaro/hylianbox` deixaria `.gitmodules` apontando para uma origem inexistente.

O commit de importação deve manter o commit final do HylianBox como segundo pai, preservando auditoria e autoria.

### ADR-003 — Propriedade do host

O SwtFrontend será a fonte de verdade para:

- biblioteca agregada e IDs globais;
- ROMs importadas pelo usuário, patches e cache preparado;
- saves, states, capturas e backups;
- conta, perfil e credenciais RetroAchievements;
- downloads, browser de fallback, tema, idioma e controles;
- índice, instalação, atualização e compatibilidade de módulos.

### ADR-004 — Propriedade do HylianBox

O módulo HylianBox será responsável por:

- cores Mupen64Plus/Parallel N64 e configuração específica;
- reconhecimento e tabelas de compatibilidade de OoT/MM;
- overlay Zelda, cujo layout permanece congelado;
- Item Tracker, Auto Ocarina e modos Pro/Standard;
- ligação do rcheevos à memória do core e hash da ROM final;
- toda UI exibida durante gameplay.

## 3. Estado atual e lacunas

O SwtFrontend ainda depende diretamente de `:libretrodroid` e `:rcheevos`, baixa cores por `CoreUpdaterImpl` e executa jogos em `GameActivity`. Portanto, ainda não é agnóstico.

O HylianBox ainda é um app completo com launcher, biblioteca, loja, browser, perfil RA, backup, dashboard e configurações. Também contém player, patchers BPS/IPS/xdelta, ZIP/7z/RAR, store, saves, tracker, Auto Ocarina e overlays.

Há sobreposição em LibretroDroid, rcheevos, UI Switch, dashboard, player e storage. Cada capacidade deve terminar com um único proprietário. Duplicação temporária exige fase explícita de remoção.

## 4. Topologia alvo

```text
SwtFrontend/
├── app/                    # host; sem emulador no estado final
├── module-api/             # modelos portáveis de catálogo/launch
├── module-sdk/             # contratos Android: discovery, Binder e Intents
├── patching/               # BPS, IPS e xdelta genéricos
├── archives/               # ZIP, 7z e RAR seguros
└── modules/
    └── hylianbox/
        ├── app/            # APK-companheiro sem launcher
        ├── libretrodroid/  # duplicata temporária
        └── ...
```

No estado final, o host não conterá `*_libretro*.so`, rcheevos ligado a core ou UI de gameplay. Deve iniciar normalmente sem módulo instalado e oferecer a instalação necessária.

## 5. Contrato v1

### 5.1 Identidade

Cada módulo declara `apiVersion`, `moduleId`, package, versão, `minHostApi`, `maxHostApi`, SHA-256 do certificado, ABIs, capacidades e versão do schema de dados. O HylianBox usa o ID estável `br.com.redclaw.hylianbox`.

O host rejeita ID duplicado, API incompatível, package divergente, assinatura inesperada ou metadata inválida.

### 5.2 Descoberta

O APK expõe um `Service` com action `br.com.redclaw.swt.module.BIND`, metadata `apiVersion`/`moduleId` e contrato Binder versionado. O host usa `PackageManager.queryIntentServices` e valida pacote e assinatura antes do bind.

Na primeira versão, host e HylianBox podem compartilhar permission `signature`. Módulos de terceiros exigirão allowlist de certificados e checagem de identidade em cada chamada.

### 5.3 Capacidades

- `CatalogProvider`: catálogo especializado;
- `GameMatcher`: reconhecimento de ROM;
- `EmulatorProvider`: execução de sessão;
- `OverlayProvider`: controles específicos;
- `ToolProvider`: Tracker, Auto Ocarina etc.;
- `AchievementRuntimeProvider`: core + rcheevos;
- `SettingsProvider`: preferências exclusivas.

Capacidade ausente nunca será inferida pelo nome do pacote.

### 5.4 Jogos e ROMs

IDs globais seguem `<moduleId>:<moduleGameId>`. Título, nome de arquivo e caminho nunca são chave. O descritor inclui plataforma, jogo-base, hashes CRC32/SHA-1/SHA-256, byte order aceito, capa, metadata e estado de instalação.

### 5.5 Sessão

O host cria `LaunchRequest` versionado contendo `sessionId`, ID global, URI `content://` da ROM final, URIs de saves/states, idioma/tema/controles, opções versionadas e handle opaco para RA.

Somente permissões URI necessárias são concedidas e depois revogadas. Senha, API key ou token bruto não entram em Intent, log ou arquivo compartilhado.

O módulo retorna `LaunchResult` com motivo da saída, tempo jogado, save alterado e erro sanitizado.

### 5.6 Ferramentas

Ferramentas têm ID, título localizado, ícone, condição de disponibilidade e ação explícita/PendingIntent. Menus de gameplay permanecem na Activity do HylianBox; o host apenas lista e aciona pontos autorizados.

## 6. Distribuição e instalação

O host consumirá um índice HTTPS `modules-v1.json` com package, versão, URL/tamanho/SHA-256 do APK, certificado, APIs compatíveis, ABIs, capacidades, changelog e licenças.

Fluxo:

1. baixar para cache privado com limites;
2. validar tamanho e SHA-256;
3. validar package, versão e certificado do APK;
4. abrir o instalador do sistema com confirmação;
5. redescobrir o serviço e validar a assinatura novamente.

Não haverá instalação silenciosa nem carregamento de código nativo extraído de arquivos arbitrários.

## 7. Dados e migração

Namespace lógico:

```text
modules/<moduleId>/games/<globalGameId>/{rom,patches,sram,states,captures}
```

Requisitos: escrita atômica; manifest com versão/tamanho/hashes; nenhuma colisão entre hacks; streaming; limites de extração; saves preservados ao remover módulo; backups legíveis sem módulo.

O package do HylianBox será preservado na primeira conversão para manter seus dados privados. Depois, um `MigrationProvider` de uso único transfere base ROMs cadastradas, hacks, patches, saves, states, capas e preferências. A migração usa journal idempotente, hashes e rollback; reexecução não duplica dados.

## 8. Patches, arquivos e browser

Patching e archives serão serviços genéricos do host: BPS, IPS, xdelta, ZIP, 7z e RAR, com validação de magic bytes, tamanho/checksum, proteção contra path traversal/zip bomb, cancelamento, progresso e saída em cache privado até a validação.

O browser interno é fallback para autenticação, download ou conteúdo inacessível por HTTP direto. Downloads do WebView entram na mesma fila segura. O projeto nunca distribui ROMs-base; somente patches, metadata e conteúdo licenciado.

## 9. RetroAchievements

O host é dono de login, perfil e credenciais protegidas. O módulo recebe apenas um broker/handle temporário. O HylianBox mantém leitura de memória, ciclo rcheevos, hash da ROM final e eventos de unlock. Hardcore fica desligado por padrão; leaderboards não são sobrepostos automaticamente; a notice MIT é preservada.

## 10. Fases, entregáveis e aceite

### F0 — Baseline

Entregáveis: builds/testes dos dois apps; inventário de manifests/JNI/prefs/storage; mapa de capacidade; fixtures sintéticas.

Aceite: builds reproduzíveis, nenhuma ROM/credencial versionada e nenhum arquivo local do usuário sobrescrito.

### F1 — Monorepo

Entregáveis: histórico em `modules/hylianbox`; licenças/headers preservados; caches, `.cxx`, APKs, keystores e `local.properties` excluídos.

Aceite: clone limpo contém fontes necessárias; o commit original é pai histórico do import; `git log` alcança a história anterior.

### F2 — API, SDK e descoberta

Entregáveis: `:module-api`, `:module-sdk`, discovery/assinatura, Service Hylian e remoção do launcher.

Aceite: host distingue ausente/instalado/incompatível/assinatura errada; HylianBox não aparece no launcher; host funciona sem ele.

### F3 — Patching, archives e catálogo

Entregáveis: `:patching`, `:archives`, IDs globais, importação/cache validados e catálogo Zelda adaptado.

Aceite: golden tests BPS/IPS/xdelta/ZIP/7z/RAR; proteção de extração; ROM grande sem carga integral na heap; CTA de instalação no catálogo.

### F4 — Execução e saves

Entregáveis: `LaunchRequest`/`LaunchResult`, URI grants mínimos, GameActivity do módulo e storage de SRAM/state.

Aceite: launch/pause/resume/exit/autosave; saves isolados por jogo; Back abre menu; retomada após morte de processo.

### F5 — Ferramentas HylianBox

Entregáveis: Tracker, Auto Ocarina, overlay congelado, Pro/Standard, settings N64 e tools SDK.

Aceite: paridade com standalone; controles físicos/touch; ferramentas somente para jogos compatíveis.

### F6 — RetroAchievements

Entregáveis: credenciais/perfil no host, broker de sessão, runtime no módulo e migração de metadata.

Aceite: segredo não deixa storage seguro; hash da ROM final; unlock/profile offline/online; logs sanitizados.

### F7 — Dados e remoção da UI standalone

Entregáveis: migração idempotente; remover launcher/home/grid/loja/browser/settings globais do HylianBox; fluxo de upgrade.

Aceite: snapshot real migra com contagens/hashes iguais; repetição não duplica; rollback preserva origem; módulo só abre pelo host.

### F8 — Desacoplamento final

Entregáveis: remover LibretroDroid/rcheevos/player/core updater do host; consolidar duplicatas nativas e notices.

Aceite: APK host sem cores; build/teste com módulo ausente; instalar/remover/atualizar não perde biblioteca/saves.

### F9 — QA, publicação e encerramento do remoto antigo

QA mínimo: pt-BR/en/es; Android 24/35; ABIs do módulo; offline; atualização; downgrade recusado; certificado divergente; backup/restauração.

Gate obrigatório antes de apagar `zonaro/hylianbox`:

1. migração publicada em `zonaro/SwtFrontend`;
2. clone novo validado em diretório vazio;
3. histórico HylianBox acessível no clone;
4. builds/testes host e módulo verdes no clone;
5. bundle Git de recuperação criado e verificado;
6. releases/workflows/URLs que apontavam ao remoto antigo atualizados;
7. somente então executar a exclusão remota.

## 11. Testes obrigatórios

- contrato: IDs únicos, API N/N-1, metadata inválida, duplicidade, cert/package divergentes, Binder morto;
- segurança: APK/hash inválido, ZIP Slip/bomb, archive truncado, URI expirada, segredo em log, patch com checksum incorreto;
- funcional: módulo ausente/instalação/update, importação legal, patch sintético, sessão, SRAM/states, tools, RA online/offline, backup sem módulo;
- manifests: um launcher apenas; serviços explicitamente exportados/protegidos;
- APKs: uma implementação de LibretroDroid/rcheevos por processo e ABI.

Comandos mínimos durante a transição:

```bash
./gradlew :module-api:test :module-sdk:test :app:testDebugUnitTest :app:assembleDebug --console=plain
./modules/hylianbox/gradlew -p modules/hylianbox test assembleDebug --console=plain
```

## 12. Rollback

Usar feature flags para registry/launch; preservar o standalone até F7; migrar dados por cópia até confirmar hashes; nunca apagar saves automaticamente; preservar APK anterior para teste; não remover o remote antigo antes do gate F9.

## 13. Riscos

- APK fora da Play exige confirmação/fontes desconhecidas e pode sofrer restrições de loja;
- Binder/Bundle exige versionamento rigoroso;
- processos não compartilham View/GL, então gameplay/overlay ficam no módulo;
- dados entre packages exigem Provider/URI explícito;
- perder a chave de assinatura impede upgrade;
- xdelta/RAR/7z/JNI exigem auditoria de licença e ABI;
- catálogos e archives são entrada não confiável;
- migração sem journal pode duplicar ou corromper dados.

## 14. Definition of Done

- host opera sem emulador embutido;
- HylianBox é instalado/atualizado/descoberto como módulo assinado;
- host possui biblioteca, browser, downloads, patches, archives, saves, backup e perfil RA;
- módulo possui core, gameplay, Tracker, Auto Ocarina, overlay, modos e runtime RA;
- não há launcher/UI central duplicada no HylianBox;
- migração é idempotente e testada;
- testes unitários, integração, instrumentação e hardware passam;
- licenças/código-fonte/notices estão completos;
- história antiga existe no monorepo e em bundle recuperável;
- apagar `zonaro/hylianbox` não quebra clone, build ou download.

## 15. Progresso desta execução

- [x] arquitetura alvo e propriedade definidas;
- [x] estratégia sem submodule definida;
- [x] histórico importado em `modules/hylianbox`;
- [x] `:module-api` e testes iniciais implementados;
- [x] `:module-sdk`, discovery e validação de assinatura implementados;
- [x] HylianBox convertido em companion sem launcher;
- [ ] baseline completo dos dois APKs;
- [ ] patching/archives extraídos;
- [ ] sessão host → módulo;
- [ ] tools e RetroAchievements migrados;
- [ ] emulação removida do host;
- [ ] publicação e clone limpo validados;
- [ ] repositório remoto antigo excluído.
