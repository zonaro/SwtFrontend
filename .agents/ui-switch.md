# UI Switch — Regras Visuais (SwtFrontend)

> **Views puro, sem Compose.** Estética Nintendo Switch do HylianBox, adaptada para launcher. 1 tema DayNight + 12 accents, foco D-pad, tint em runtime, `ScaledAppCompatActivity` before `super.onCreate()`.

## Tokens (colors.xml)

- **`values/colors.xml` (90 linhas) + `values-night/colors.xml` (73 linhas) idênticos ao HylianBox.** `switch_bg`/`switch_bg_dark|light` (`#2D2D2D`/`#F0F0F0`), `switch_panel`/`_dark|_light` (`#1E1E1E`/`#FFFFFF`), `switch_dialog_bg`, `switch_text_primary` (`#FFFFFF` dark / `#333333` light), `switch_text_secondary`, `switch_scrim`, `switch_dock_circle`, `switch_card_dim`, `splash_text` (`#F5E9C8`), `color_*` M3 mappings (`color_background→switch_bg`, `color_surface→panel`, `color_primary` default `#00BCD4` até accent overlay).
- **12 `accent_*`:** `accent_cyan #00BCD4`, `accent_green_light #4CAF50`, `accent_green_dark #2E7D32`, `accent_blue #2196F3`, `accent_yellow #FFEB3B`, `accent_pink #E91E63`, `accent_red #F44336`, `accent_violet #9C27B0`, `accent_teal #009688`, `accent_orange #FF9800`, `accent_purple #673AB7`, `accent_indigo #3F51B5`. Keys batem `AccentOption` enum (HylianBox) e `AccentManager`.

## Themes (themes.xml)

- **`Theme.Swt` parent `Theme.Material3.DayNight.NoActionBar`:** `android:windowBackground @color/switch_bg`, `statusBar/navigationBar transparent`, `colorPrimary/OnPrimary/Secondary/OnSecondary/Error/OnError` → `color_*`, `colorSurface` etc. → `switch_*`.
- **12 `ThemeOverlay.Swt.Accent.*` parent `ThemeOverlay.Material3`:** cada um sobrescreve `colorPrimary/container`, `colorSecondary`, `colorAccent`/`android:colorAccent`, `colorControlActivated`, `colorOnPrimary/container` (`#000000` para cyan/green_light/yellow, branco para demais). Aplicado por `ScaledAppCompatActivity` antes de `inflate` (`theme.applyStyle(overlay, true)`).
- **Extras Fase 2A:** `BottomSheet` (`Theme.HylianBox.BottomSheet` renomeado), `Splash` (`Theme.Swt.Splash` com `splash_artwork.xml`), `SwitchButton` (`Widget.Swt.SwitchButton` com `bg_switch_button[_focus]`), `SwitchSwitch` (`switch_thumb/track` color states), `SwitchSectionHeader`, `SwitchSettingsNavigationRow` (`bg_settings_nav_row[_focus|active]`), `SwitchTextField`/`SwitchEditText` (`switch_text_field_stroke`), `SwitchDialogTheme` (`bg_switch_dialog` + `switch_dialog_bg`), `GameplayFullscreenDialogTheme`, `SwitchDialogAnimation` (`slide_in_up/out_down`), `SettingRow`.

## ThemeManager + AccentManager + SwtPrefs + ScaledAppCompatActivity

- **`theme/ThemeManager.kt`:** `AppCompatDelegate.setDefaultNightMode` (`MODE_NIGHT_YES/NO/FOLLOW_SYSTEM`), persistido em `SwtPrefs` (`theme_mode`), `applyAtStartup()` em `SwtApp.onCreate` (antes de qualquer `Activity`), recria `Activity` ao trocar.
- **`theme/AccentManager.kt`:** `enum AccentOption` 12 valores, `SharedPreferences` `accent_option` (Cyan default), `getCurrentAccent()`, `setAccent()`, `getOverlayRes()` (mapeia para `ThemeOverlay.Swt.Accent.*`), helpers `drawableBuilders`: `focusBorder(accent)`, `badge(accent)`, `ripple(accent)`, `buttonSelector`, `thumb/track` tint. Tudo com `setColorFilter(accent, PorterDuff.Mode.SRC_IN)` em `ImageView`/drawable.
- **`utils/SwtPrefs.kt`:** wrapper `SharedPreferences` (`swt_prefs`) para `theme_mode` + `accent_option` (usado também por `scraping/SwtPrefs.kt` para chaves IGDB/TGB/SGDB e `SwtApp` para RA prefs).
- **`views/ScaledAppCompatActivity.kt`:** base de todas as `Activity` (`Library`, `GameGrid`, `Apps`, `Settings`, `Game`, RA). Antes de `super.onCreate()` resolve `ThemeManager`/`AccentManager` e `theme.applyStyle(overlay, true)`. HylianBox tinha `UiScaleManager`/`SwitchBackButton` removidos (sem Compose).

## Drawables, dimens, anim, color states

- **Drawables:** `bg_switch_button.xml` (rounded `switch_panel` + stroke), `bg_switch_button_focus.xml` (stroke `?attr/colorPrimary` 3dp), `bg_switch_dialog.xml` (rounded `switch_dialog_bg`), `bg_settings_nav_row.xml` (rounded `switch_panel` + ripple), `bg_settings_nav_focus.xml` (stroke accent), `bg_settings_nav_active.xml` (fill accent 20%), `bg_settings_section.xml` (rounded panel), `splash_artwork.xml` (layer-list, warm white `#F5E9C8` wordmark).
- **Color states:** `switch_thumb.xml` (checked → accent, unchecked → `#9E9E9E`), `switch_track.xml` (checked → accent 50%, unchecked → `#555555`), `switch_text_field_stroke.xml` (focused → accent).
- **Dimens:** `dimens.xml` 40+ tokens: `switch_screen_margin 16dp`, `switch_home_row_top_spacing 24dp`, `switch_label_focused_text 18sp`, `switch_card_width 160dp`, `switch_dock_circle 64dp`, etc. (copiados de HylianBox `dimens.xml`).
- **Anim:** `slide_in_up.xml` (`translateY 100%→0% 220ms`), `slide_out_down.xml` inverso, usado por `SwitchDialogTheme` + `RaOverlayView`.

## RetroArch monochrome icons

- **Origem:** `libretro/retroarch-assets` shallow `git clone --depth 1` → `monochrome/*.png` (0.5–5 KB, `24dp`, 1-bit alpha).
- **Destino:** `app/src/main/res/drawable-nodpi/ic_dock_*` 12 PNGs: `ic_dock_games (favorites.png)`, `ic_dock_apps (menu_add.png)`, `ic_dock_achievements (menu_achievements.png)`, `ic_dock_profile (menu_user.png)`, `ic_dock_settings (settings.png)`, `ic_dock_all_games (folder.png)`, `ic_dock_collections (menu_playlist.png)`, `ic_dock_quickmenu (menu_quickmenu.png)`, `ic_dock_network (menu_network.png)`, `ic_dock_exit (menu_exit.png)`, `ic_dock_help (menu_help.png)`, `ic_dock_info (menu_info.png)`.
- **Helper:** `ui/RetroArchIcons.kt` enum `DockIcon` com `resId` + `contentDescription` + `tint(accent)` helper (`imageView.setColorFilter(accent)`). Layout `layout_dock_button.xml` (FrameLayout círculo `switch_dock_circle` + ImageView `switch_dock_icon`).
- **Regra:** só `ic_dock_*` são tintados por accent; `ic_trophy` (RA) e `splash_artwork` são fixos (warm white).

## Layouts principais

- **`activity_library.xml`:** `LinearLayout` vertical: `Space top_spacing` + `TextView library_focused_label` (bold `?attr/colorPrimary`) + `RecyclerView library_home_row` (horizontal, `clipToPadding false`, `overScroll never`) + `RecyclerView library_cards_grid` (grid 2 col, `Coleções/Todos os Jogos/Apps/Dashboard`) + `Space weight 1` + `LinearLayout library_dock` (center, 5 buttons). `DockAdapter` infla `layout_dock_button.xml`.
- **`activity_game.xml`:** `FrameLayout retro_view_container` (GLRetroView programático) + `GameTouchOverlay` (custom View d-pad/A/B/L/R) + `menu_overlay` (FrameLayout `View.GONE`, `bg scrim`, botões Resume/Save/Load/Reset/Quit, `frameSpeed=0` pause) + `RaOverlayView` (top, unlock toast `RaOverlayView`).
- **`activity_game_grid.xml`:** `Toolbar` + `EditText search` (`SwitchTextField` stroke accent) + `Spinner sort` (`alpha/last_played/recent`) + `RecyclerView` grid `SpanSize lookup` + `TextView empty`.
- **`activity_apps.xml`:** `RecyclerView` seções por `CategoryTag` (Kwiq `ApplicationInfo.CATEGORY_*` + `CATEGORY_GAME` etc.), `item_app.xml` (icon 48dp + label + category badge tint accent).
- **`activity_settings.xml`:** `ScrollView` + `SwitchSectionHeader` + `SwitchSettingsNavigationRow` (ripple + focus) para ROMs (SAF), Appearance (accent spinner 12 + theme radio), Providers (4 EditText keys), RA (username/password + token + Web API key via `InternalSimpleBrowser` capture `controlpanel.php`), Dashboard (toggle + port + `dashboard_notif_active`), Cores download (progress).

## Foco e D-pad

- Todos os `RecyclerView` têm `focusable false` para o container, itens `focusable true` + `background bg_switch_button[_focus]`. Dock buttons são `FrameLayout` com `onFocusChangeListener` → tint accent. `LibraryActivity` `HomeGamesAdapter` registra `focused_label` ao mover D-pad. Não há Compose `FocusRequester`.
