/* SwtFrontend Dashboard — app.js */
(function() {
    'use strict';

    // ─── State ──────────────────────────────────────────────────────────────
    let currentUser = null;
    let currentRomKey = null;

    // ─── Init ───────────────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', async () => {
        await checkSession();
        setupNav();
        setupUpload();
        setupModal();
        setupSearch();
        loadPage(getCurrentPage());
    });

    // ─── Session ────────────────────────────────────────────────────────────
    async function checkSession() {
        try {
            const resp = await api('/api/session');
            currentUser = resp;
            document.getElementById('user-display').textContent = resp.username;
        } catch {
            window.location.href = '/login';
        }
    }

    // ─── Navigation ─────────────────────────────────────────────────────────
    function getCurrentPage() {
        const params = new URLSearchParams(window.location.search);
        return params.get('page') || 'home';
    }

    function setupNav() {
        document.querySelectorAll('.nav-link').forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const page = link.dataset.page;
                navigateTo(page);
            });
        });
        document.getElementById('btn-logout').addEventListener('click', async () => {
            await api('/api/logout', { method: 'POST' });
            window.location.href = '/login';
        });
    }

    function navigateTo(page) {
        history.pushState({}, '', '?page=' + page);
        loadPage(page);
    }

    window.addEventListener('popstate', () => loadPage(getCurrentPage()));

    function loadPage(page) {
        document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
        document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));

        const pageEl = document.getElementById('page-' + page);
        const navEl = document.querySelector('[data-page="' + page + '"]');
        if (pageEl) pageEl.classList.add('active');
        if (navEl) navEl.classList.add('active');

        switch (page) {
            case 'home': loadDashboard(); break;
            case 'library': loadLibrary(); break;
            case 'settings': loadSettings(); break;
        }
    }

    // ─── API helper ─────────────────────────────────────────────────────────
    async function api(url, options = {}) {
        const resp = await fetch(url, {
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', ...options.headers },
            ...options
        });
        if (resp.status === 401) {
            window.location.href = '/login';
            throw new Error('Unauthorized');
        }
        if (url.includes('/rom/download') || url.includes('/rom/cover') || url.includes('/rom/background')) {
            return resp;
        }
        return resp.json();
    }

    // ─── Home / Dashboard ───────────────────────────────────────────────────
    async function loadDashboard() {
        try {
            const data = await api('/api/dashboard');
            const lib = data.library || {};
            document.getElementById('stat-roms').textContent = lib.totalRoms || 0;
            document.getElementById('stat-platforms').textContent = lib.platformCount || 0;
            document.getElementById('stat-storage').textContent = (data.storage || {}).freeText || '--';
            document.getElementById('stat-port').textContent = (data.server || {}).port || '--';
            document.getElementById('server-url').textContent = window.location.origin;

            // Simple QR using a canvas-based generator
            generateQR(window.location.origin);
        } catch (e) {
            console.error('Dashboard load error:', e);
        }
    }

    // ─── Library ────────────────────────────────────────────────────────────
    let allRoms = [];

    async function loadLibrary() {
        try {
            const data = await api('/api/gaming/roms');
            allRoms = data.roms || [];
            renderRoms(allRoms);
        } catch (e) {
            console.error('Library load error:', e);
            document.getElementById('rom-list').innerHTML = '<p style="color:var(--text-muted)">Nenhuma ROM encontrada. Faça upload para começar.</p>';
        }
    }

    function renderRoms(roms) {
        const container = document.getElementById('rom-list');
        if (!roms.length) {
            container.innerHTML = '<p style="color:var(--text-muted)">Nenhuma ROM encontrada. Faça upload para começar.</p>';
            return;
        }
        container.innerHTML = roms.map(rom => {
            const coverUrl = rom.hasCover ? `/api/gaming/rom/cover?key=${encodeURIComponent(rom.documentUri)}` : '';
            const size = formatSize(rom.size);
            return `
                <div class="rom-card" data-key="${escapeAttr(rom.documentUri)}">
                    ${coverUrl ? `<img class="rom-card-cover" src="${escapeAttr(coverUrl)}" alt="${escapeAttr(rom.name)}" loading="lazy">` : '<div class="rom-card-cover" style="display:flex;align-items:center;justify-content:center;color:var(--text-muted)">Sem capa</div>'}
                    <div class="rom-card-header">
                        <div class="rom-card-name">${escapeHtml(rom.name)}</div>
                        <span class="rom-card-platform">${escapeHtml(rom.platform)}</span>
                    </div>
                    <div class="rom-card-meta">.${escapeHtml(rom.extension)} &middot; ${size}</div>
                </div>`;
        }).join('');

        container.querySelectorAll('.rom-card').forEach(card => {
            card.addEventListener('click', () => openRomModal(card.dataset.key));
        });
    }

    // ─── Search ─────────────────────────────────────────────────────────────
    function setupSearch() {
        const input = document.getElementById('rom-search');
        if (!input) return;
        input.addEventListener('input', () => {
            const q = input.value.toLowerCase();
            const filtered = allRoms.filter(r =>
                r.name.toLowerCase().includes(q) || r.platform.toLowerCase().includes(q)
            );
            renderRoms(filtered);
        });
    }

    // ─── Upload ─────────────────────────────────────────────────────────────
    function setupUpload() {
        const btn = document.getElementById('btn-upload');
        const input = document.getElementById('file-input');
        if (!btn || !input) return;
        btn.addEventListener('click', () => input.click());
        input.addEventListener('change', async () => {
            for (const file of input.files) {
                await uploadRom(file);
            }
            input.value = '';
            loadLibrary();
        });
    }

    async function uploadRom(file) {
        const form = new FormData();
        form.append('file', file);
        form.append('originalName', file.name);
        try {
            await api('/api/gaming/upload', { method: 'POST', body: form, headers: {} });
        } catch (e) {
            console.error('Upload error:', e);
            alert('Falha ao upload: ' + e.message);
        }
    }

    // ─── ROM Modal ──────────────────────────────────────────────────────────
    function setupModal() {
        document.getElementById('modal-close').addEventListener('click', closeModal);
        document.getElementById('rom-modal').addEventListener('click', (e) => {
            if (e.target === e.currentTarget) closeModal();
        });
        document.getElementById('cover-input').addEventListener('change', async (e) => {
            if (!currentRomKey || !e.target.files[0]) return;
            await uploadCover(currentRomKey, e.target.files[0]);
            openRomModal(currentRomKey);
        });
        document.getElementById('btn-clear-cover').addEventListener('click', async () => {
            if (!currentRomKey) return;
            await api('/api/gaming/rom/cover?key=' + encodeURIComponent(currentRomKey), { method: 'DELETE' });
            openRomModal(currentRomKey);
        });
        document.getElementById('btn-delete-rom').addEventListener('click', async () => {
            if (!currentRomKey) return;
            if (!confirm('Excluir esta ROM?')) return;
            await api('/api/gaming/rom?key=' + encodeURIComponent(currentRomKey), { method: 'DELETE' });
            closeModal();
            loadLibrary();
        });
    }

    async function openRomModal(key) {
        currentRomKey = key;
        try {
            const data = await api('/api/gaming/rom?key=' + encodeURIComponent(key));
            const rom = data.rom;
            document.getElementById('modal-rom-name').textContent = rom.name;
            document.getElementById('modal-platform').textContent = rom.platform;
            document.getElementById('modal-extension').textContent = '.' + rom.extension;
            document.getElementById('modal-size').textContent = formatSize(rom.size);

            const coverImg = document.getElementById('modal-cover');
            coverImg.src = rom.hasCover
                ? '/api/gaming/rom/cover?key=' + encodeURIComponent(key)
                : '';

            document.getElementById('btn-download').href = '/api/gaming/rom/download?key=' + encodeURIComponent(key);
            document.getElementById('rom-modal').classList.remove('hidden');
        } catch (e) {
            console.error('ROM detail error:', e);
        }
    }

    function closeModal() {
        document.getElementById('rom-modal').classList.add('hidden');
        currentRomKey = null;
    }

    async function uploadCover(key, file) {
        const form = new FormData();
        form.append('file', file);
        try {
            await api('/api/gaming/rom/cover?key=' + encodeURIComponent(key), {
                method: 'POST',
                body: form,
                headers: {}
            });
        } catch (e) {
            console.error('Cover upload error:', e);
        }
    }

    // ─── Settings ───────────────────────────────────────────────────────────
    async function loadSettings() {
        try {
            const data = await api('/api/settings');
            document.getElementById('setting-port').textContent = data.port || '--';
            document.getElementById('setting-roms-dir').textContent = data.romsDir || '--';
            document.getElementById('setting-covers-dir').textContent = data.coversDir || '--';
        } catch (e) {
            console.error('Settings load error:', e);
        }
    }

    // ─── QR Code (simple canvas) ───────────────────────────────────────────
    // Minimal QR code generator for URLs (uses a tiny implementation)
    function generateQR(text) {
        const container = document.getElementById('qr-container');
        if (!container) return;
        container.innerHTML = '';
        // Use a simple approach: create an image tag pointing to a QR API
        // (works offline-free without a JS QR library)
        const canvas = document.createElement('canvas');
        canvas.width = 200;
        canvas.height = 200;
        const ctx = canvas.getContext('2d');
        // Draw a placeholder with the URL text
        ctx.fillStyle = '#16213e';
        ctx.fillRect(0, 0, 200, 200);
        ctx.fillStyle = '#e94560';
        ctx.font = '12px monospace';
        ctx.textAlign = 'center';
        const lines = text.split('/');
        ctx.fillText(lines[2] || text, 100, 90);
        ctx.fillText('Porta: ' + (lines[3] || ''), 100, 110);
        container.appendChild(canvas);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────
    function formatSize(bytes) {
        if (!bytes || bytes === 0) return '0 B';
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    function escapeAttr(str) {
        return str.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/'/g, '&#39;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
})();
