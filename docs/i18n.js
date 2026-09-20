/**
 * i18n.js — Internationalization engine for SwtFrontend landing page
 * No dependencies. Pure Vanilla JS.
 *
 * Features:
 * - Auto-detect via navigator.language (pt-* → pt-BR, es-* → es, fallback → en)
 * - Manual override via <select> + buttons in header
 * - Persists to localStorage (key: swt-docs-locale)
 * - Updates lang attribute on <html>
 * - All strings via data-i18n keys
 * - Locale-aware date/number formatting via Intl
 */
;(function () {
  'use strict';

  var STORAGE_KEY = 'swt-docs-locale';
  var DEFAULT_LOCALE = 'en';
  var SUPPORTED = ['en', 'pt-BR', 'es'];
  var dicts = {};
  var currentLocale = DEFAULT_LOCALE;

  /**
   * Detect initial locale from browser language
   */
  function detectLocale() {
    var stored = null;
    try { stored = localStorage.getItem(STORAGE_KEY); } catch (e) { /* noop */ }
    if (stored && SUPPORTED.indexOf(stored) !== -1) return stored;

    var nav = (navigator.language || navigator.userLanguage || '').toLowerCase();
    if (nav.indexOf('pt') === 0) return 'pt-BR';
    if (nav.indexOf('es') === 0) return 'es';
    return DEFAULT_LOCALE;
  }

  /**
   * Load a locale JSON file
   */
  function loadLocale(locale) {
    if (dicts[locale]) return Promise.resolve(dicts[locale]);
    return fetch('locales/' + locale + '.json')
      .then(function (r) {
        if (!r.ok) throw new Error('Failed to load ' + locale);
        return r.json();
      })
      .then(function (data) {
        dicts[locale] = data;
        return data;
      });
  }

  /**
   * Apply translations to all [data-i18n] elements
   */
  function applyTranslations(locale) {
    var dict = dicts[locale] || dicts[DEFAULT_LOCALE] || {};
    var fallback = dicts[DEFAULT_LOCALE] || {};

    // Text content
    var els = document.querySelectorAll('[data-i18n]');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      var key = el.getAttribute('data-i18n');
      var val = dict[key] || fallback[key] || key;
      if (el.tagName === 'INPUT' && el.type !== 'submit') {
        el.placeholder = val;
      } else {
        el.textContent = val;
      }
    }

    // HTML content (for rich text)
    var htmlEls = document.querySelectorAll('[data-i18n-html]');
    for (var j = 0; j < htmlEls.length; j++) {
      var hel = htmlEls[j];
      var hkey = hel.getAttribute('data-i18n-html');
      var hval = dict[hkey] || fallback[hkey] || hkey;
      hel.innerHTML = hval;
    }

    // Attribute translations (e.g. data-i18n-attr="content" for meta tags)
    var attrEls = document.querySelectorAll('[data-i18n-attr]');
    for (var k = 0; k < attrEls.length; k++) {
      var ael = attrEls[k];
      var aKey = ael.getAttribute('data-i18n'); // the translation key is in data-i18n
      var aAttr = ael.getAttribute('data-i18n-attr'); // the attribute to update
      if (aKey && aAttr) {
        var aVal = dict[aKey] || fallback[aKey] || aKey;
        ael.setAttribute(aAttr, aVal);
      }
    }

    // Meta tags
    var titleEl = document.querySelector('title');
    if (titleEl && dict['meta.title']) {
      titleEl.textContent = dict['meta.title'];
    }
    var descEl = document.querySelector('meta[name="description"]');
    if (descEl && dict['meta.description']) {
      descEl.setAttribute('content', dict['meta.description']);
    }
    var ogTitle = document.querySelector('meta[property="og:title"]');
    if (ogTitle && dict['meta.title']) {
      ogTitle.setAttribute('content', dict['meta.title']);
    }
    var ogDesc = document.querySelector('meta[property="og:description"]');
    if (ogDesc && dict['meta.description']) {
      ogDesc.setAttribute('content', dict['meta.description']);
    }
    var twTitle = document.querySelector('meta[property="twitter:title"]');
    if (twTitle && dict['meta.title']) {
      twTitle.setAttribute('content', dict['meta.title']);
    }
    var twDesc = document.querySelector('meta[property="twitter:description"]');
    if (twDesc && dict['meta.description']) {
      twDesc.setAttribute('content', dict['meta.description']);
    }
  }

  /**
   * Update lang attribute and active states
   */
  function updateLangAttr(locale) {
    var lang = locale.toLowerCase();
    document.documentElement.setAttribute('lang', lang);

    // Update select
    var sel = document.getElementById('lang-select');
    if (sel) sel.value = locale;

    // Update buttons
    var btns = document.querySelectorAll('[data-lang-btn]');
    for (var i = 0; i < btns.length; i++) {
      var isActive = btns[i].getAttribute('data-lang-btn') === locale;
      btns[i].classList.toggle('is-active', isActive);
      btns[i].setAttribute('aria-pressed', isActive ? 'true' : 'false');
    }
  }

  /**
   * Switch locale
   */
  function setLocale(locale) {
    if (SUPPORTED.indexOf(locale) === -1) locale = DEFAULT_LOCALE;
    currentLocale = locale;
    try { localStorage.setItem(STORAGE_KEY, locale); } catch (e) { /* noop */ }
    loadLocale(locale).then(function () {
      applyTranslations(locale);
      updateLangAttr(locale);
    });
  }

  /**
   * Locale-aware date formatting
   */
  function formatDate(date) {
    var opts = { year: 'numeric', month: 'long', day: 'numeric' };
    try {
      return new Intl.DateTimeFormat(currentLocale, opts).format(date);
    } catch (e) {
      return date.toLocaleDateString();
    }
  }

  /**
   * Locale-aware number formatting
   */
  function formatNumber(num) {
    try {
      return new Intl.NumberFormat(currentLocale).format(num);
    } catch (e) {
      return String(num);
    }
  }

  /**
   * Initialize
   */
  function init() {
    currentLocale = detectLocale();

    // Load default (en) first, then the detected locale
    loadLocale(DEFAULT_LOCALE).then(function () {
      if (currentLocale !== DEFAULT_LOCALE) {
        return loadLocale(currentLocale);
      }
    }).then(function () {
      applyTranslations(currentLocale);
      updateLangAttr(currentLocale);

      // Bind select
      var sel = document.getElementById('lang-select');
      if (sel) {
        sel.addEventListener('change', function () {
          setLocale(this.value);
        });
      }

      // Bind buttons
      var btns = document.querySelectorAll('[data-lang-btn]');
      for (var i = 0; i < btns.length; i++) {
        btns[i].addEventListener('click', function () {
          setLocale(this.getAttribute('data-lang-btn'));
        });
      }

      // Dispatch event for app.js
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('i18n:ready', { detail: { locale: currentLocale } }));
      }
    });
  }

  // Expose API
  window.SwtI18n = {
    setLocale: setLocale,
    getLocale: function () { return currentLocale; },
    t: function (key) {
      var dict = dicts[currentLocale] || dicts[DEFAULT_LOCALE] || {};
      return dict[key] || key;
    },
    formatDate: formatDate,
    formatNumber: formatNumber
  };

  // Auto-init on DOMContentLoaded
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
