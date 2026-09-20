/**
 * app.js — SwtFrontend landing page interactions
 * No dependencies. Pure Vanilla JS.
 *
 * Features:
 * - Accent color switcher (12 colors, persisted in localStorage)
 * - Theme toggle (dark/light, persisted in localStorage)
 * - Smooth scroll for anchor links
 * - FAQ accordion
 * - Scroll-triggered animations (IntersectionObserver)
 * - Mobile navigation
 */
;(function () {
  'use strict';

  var ACCENT_KEY = 'swt-docs-accent';
  var THEME_KEY = 'swt-docs-theme';
  var DEFAULT_ACCENT = 'cyan';
  var DEFAULT_THEME = 'dark';

  /* ============================================================
     Helpers
     ============================================================ */
  function getStored(key, fallback) {
    try { return localStorage.getItem(key) || fallback; } catch (e) { return fallback; }
  }
  function setStored(key, val) {
    try { localStorage.setItem(key, val); } catch (e) { /* noop */ }
  }

  /* ============================================================
     Accent Color Switcher
     ============================================================ */
  function initAccent() {
    var accent = getStored(ACCENT_KEY, DEFAULT_ACCENT);
    document.documentElement.setAttribute('data-accent', accent);
    updateAccentUI(accent);

    var btns = document.querySelectorAll('[data-accent-btn]');
    for (var i = 0; i < btns.length; i++) {
      btns[i].addEventListener('click', function () {
        var c = this.getAttribute('data-accent-btn');
        document.documentElement.setAttribute('data-accent', c);
        setStored(ACCENT_KEY, c);
        updateAccentUI(c);
        // Update theme-color meta
        updateThemeColor(c);
      });
    }
  }

  function updateAccentUI(accent) {
    var btns = document.querySelectorAll('[data-accent-btn]');
    for (var i = 0; i < btns.length; i++) {
      var isActive = btns[i].getAttribute('data-accent-btn') === accent;
      btns[i].classList.toggle('is-active', isActive);
      btns[i].setAttribute('aria-pressed', isActive ? 'true' : 'false');
    }
  }

  var accentColors = {
    cyan: '#00BCD4', green_light: '#4CAF50', green_dark: '#2E7D32',
    blue: '#2196F3', yellow: '#FFEB3B', pink: '#E91E63',
    red: '#F44336', violet: '#9C27B0', teal: '#009688',
    orange: '#FF9800', purple: '#673AB7', indigo: '#3F51B5'
  };

  function updateThemeColor(accent) {
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', accentColors[accent] || '#2D2D2D');
  }

  /* ============================================================
     Theme Toggle
     ============================================================ */
  function initTheme() {
    var theme = getStored(THEME_KEY, DEFAULT_THEME);
    applyTheme(theme);

    var toggle = document.getElementById('theme-toggle');
    if (toggle) {
      toggle.addEventListener('click', function () {
        var current = document.documentElement.getAttribute('data-theme');
        var next = current === 'dark' ? 'light' : 'dark';
        applyTheme(next);
        setStored(THEME_KEY, next);
      });
    }

    // Footer theme buttons
    var themeBtns = document.querySelectorAll('[data-theme-btn]');
    for (var i = 0; i < themeBtns.length; i++) {
      themeBtns[i].addEventListener('click', function () {
        var t = this.getAttribute('data-theme-btn');
        applyTheme(t);
        setStored(THEME_KEY, t);
      });
    }
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);

    // Update toggle icon
    var sun = document.querySelector('.icon-sun');
    var moon = document.querySelector('.icon-moon');
    if (sun && moon) {
      sun.style.display = theme === 'dark' ? 'block' : 'none';
      moon.style.display = theme === 'light' ? 'block' : 'none';
    }

    // Update theme-color meta
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', theme === 'dark' ? '#2D2D2D' : '#F0F0F0');

    // Update footer theme buttons
    var btns = document.querySelectorAll('[data-theme-btn]');
    for (var i = 0; i < btns.length; i++) {
      var isActive = btns[i].getAttribute('data-theme-btn') === theme;
      btns[i].classList.toggle('is-active', isActive);
    }
  }

  /* ============================================================
     Smooth Scroll
     ============================================================ */
  function initSmoothScroll() {
    var links = document.querySelectorAll('a[href^="#"]');
    for (var i = 0; i < links.length; i++) {
      links[i].addEventListener('click', function (e) {
        var href = this.getAttribute('href');
        if (href === '#') return;
        var target = document.querySelector(href);
        if (target) {
          e.preventDefault();
          target.scrollIntoView({ behavior: 'smooth', block: 'start' });
          // Close mobile nav if open
          closeMobileNav();
        }
      });
    }
  }

  /* ============================================================
     FAQ Accordion
     ============================================================ */
  function initFAQ() {
    var items = document.querySelectorAll('.faq-item');
    for (var i = 0; i < items.length; i++) {
      var btn = items[i].querySelector('.faq-question');
      if (btn) {
        btn.addEventListener('click', function () {
          var item = this.closest('.faq-item');
          var isOpen = item.classList.contains('open');
          // Close all
          var allItems = document.querySelectorAll('.faq-item');
          for (var j = 0; j < allItems.length; j++) {
            allItems[j].classList.remove('open');
            allItems[j].querySelector('.faq-question').setAttribute('aria-expanded', 'false');
          }
          // Toggle current
          if (!isOpen) {
            item.classList.add('open');
            this.setAttribute('aria-expanded', 'true');
          }
        });
      }
    }
  }

  /* ============================================================
     Scroll Animations (IntersectionObserver)
     ============================================================ */
  function initAnimations() {
    if (typeof IntersectionObserver === 'undefined') {
      // Fallback: show all
      var els = document.querySelectorAll('.animate-in');
      for (var i = 0; i < els.length; i++) els[i].classList.add('visible');
      return;
    }

    var observer = new IntersectionObserver(function (entries) {
      for (var i = 0; i < entries.length; i++) {
        if (entries[i].isIntersecting) {
          entries[i].target.classList.add('visible');
          observer.unobserve(entries[i].target);
        }
      }
    }, { threshold: 0.1, rootMargin: '0px 0px -40px 0px' });

    var els = document.querySelectorAll('.animate-in');
    for (var j = 0; j < els.length; j++) observer.observe(els[j]);
  }

  /* ============================================================
     Mobile Navigation
     ============================================================ */
  function initMobileNav() {
    var btn = document.getElementById('mobile-nav-btn');
    var nav = document.getElementById('mobile-nav');
    var closeBtn = document.getElementById('mobile-nav-close');

    if (btn && nav) {
      btn.addEventListener('click', function () {
        nav.classList.add('open');
        document.body.style.overflow = 'hidden';
        closeBtn.focus();
      });
    }

    if (closeBtn && nav) {
      closeBtn.addEventListener('click', closeMobileNav);
    }

    // Close on link click
    var links = nav ? nav.querySelectorAll('a') : [];
    for (var i = 0; i < links.length; i++) {
      links[i].addEventListener('click', closeMobileNav);
    }

    // Close on Escape
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && nav && nav.classList.contains('open')) {
        closeMobileNav();
        btn.focus();
      }
    });
  }

  function closeMobileNav() {
    var nav = document.getElementById('mobile-nav');
    if (nav) {
      nav.classList.remove('open');
      document.body.style.overflow = '';
    }
  }

  /* ============================================================
     Initialize Everything
     ============================================================ */
  function init() {
    initAccent();
    initTheme();
    initSmoothScroll();
    initFAQ();
    initAnimations();
    initMobileNav();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
