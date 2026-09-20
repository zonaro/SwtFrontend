# SwtFrontend Landing Page

Static landing page for SwtFrontend, hosted via GitHub Pages from the `docs/` folder.

## Quick Start (Local)

```bash
cd docs/
python3 -m http.server 8000
```

Then open [http://localhost:8000](http://localhost:8000) in your browser.

## How It Works

- **`index.html`** — Semantic HTML5 page with JSON-LD, Open Graph tags, and accessible markup
- **`styles.css`** — CSS variables for dark/light themes and 12 accent colors, responsive design (375px to ultrawide)
- **`app.js`** — Accent color switcher, theme toggle, smooth scroll, FAQ accordion, scroll animations
- **`i18n.js`** — Internationalization engine (no dependencies)
- **`locales/`** — Translation files for each supported language

## i18n (Internationalization)

### Supported Languages

| Code | Language |
|------|----------|
| `en` | English (default/fallback) |
| `pt-BR` | Portugues (Brasil) |
| `es` | Espanol |

### How It Works

1. **Auto-detection**: On page load, `i18n.js` checks `navigator.language`:
   - `pt-*` → Portuguese (Brazil)
   - `es-*` → Spanish
   - Anything else → English (fallback)

2. **Manual override**: Users can switch language via:
   - Header language buttons (EN / PT / ES)
   - Footer `<select>` dropdown

3. **Persistence**: The selected language is saved in `localStorage` under key `swt-docs-locale`. On return visits, the saved choice is used instead of auto-detection.

4. **How translations work**:
   - All visible strings use `data-i18n="key"` attributes in HTML
   - Translation dictionaries live in `locales/{locale}.json`
   - On language change, `i18n.js` replaces text content of all `[data-i18n]` elements
   - Meta tags (title, description, OG) are also updated
   - The `<html lang="...">` attribute is updated to match

### Adding a New Language

1. Create `locales/{code}.json` copying the structure from `locales/en.json`
2. Add the locale code to the `SUPPORTED` array in `i18n.js`
3. Add an `<option>` to the footer `<select>` in `index.html`
4. Add a button to the header `.lang-switcher` if desired

### Translation Keys

All keys follow a dot-notation convention:

- `meta.*` — Page metadata (title, description)
- `nav.*` — Navigation links
- `hero.*` — Hero section
- `features.*` — Feature cards
- `how.*` — How it works steps
- `faq.*` — FAQ questions and answers
- `footer.*` — Footer content
- `accent.*`, `theme.*`, `lang.*` — Settings labels

## Theme & Accent System

### Dark/Light Theme

- Stored in `localStorage` key `swt-docs-theme`
- Toggle via header button or footer buttons
- Uses `data-theme="dark|light"` on `<html>`
- All colors defined as CSS variables in `:root` (dark) and `[data-theme="light"]`

### 12 Accent Colors

Stored in `localStorage` key `swt-docs-accent`. Applied via `data-accent="name"` on `<html>`.

| Accent | Color |
|--------|-------|
| `cyan` | #00BCD4 (default) |
| `green_light` | #4CAF50 |
| `green_dark` | #2E7D32 |
| `blue` | #2196F3 |
| `yellow` | #FFEB3B |
| `pink` | #E91E63 |
| `red` | #F44336 |
| `violet` | #9C27B0 |
| `teal` | #009688 |
| `orange` | #FF9800 |
| `purple` | #673AB7 |
| `indigo` | #3F51B5 |

## GitHub Pages Deployment

This folder is configured for GitHub Pages deployment from the `docs/` directory on the main branch. No build step required — all files are served as-is.

## Accessibility

- Skip-to-content link
- Semantic HTML5 landmarks (`<nav>`, `<main>`, `<footer>`)
- ARIA labels on interactive elements
- Keyboard-navigable (tab, enter, escape)
- `prefers-reduced-motion` respected (animations disabled)
- Color contrast AA compliant

## License

This landing page is part of the SwtFrontend project and is released under the GPLv3 license.
