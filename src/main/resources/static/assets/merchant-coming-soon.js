(() => {
    const panel = document.querySelector('[data-coming-soon]');
    if (!panel || !panel.dataset.storeId) return;
    const key = `ncf-coming-soon:${panel.dataset.storeId}:${panel.dataset.comingSoon}`;
    try {
        if (localStorage.getItem(key) === 'dismissed') {
            panel.hidden = true;
        }
    } catch (_) { /* Dismissal still works when browser storage is unavailable. */ }
    document.querySelectorAll('[data-coming-soon-link]').forEach(link => {
        link.addEventListener('click', event => {
            event.preventDefault();
            panel.hidden = false;
            try { localStorage.removeItem(key); } catch (_) { /* Current page only. */ }
            const heading = panel.querySelector('h2');
            if (heading) heading.focus({ preventScroll: true });
            const reducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            panel.scrollIntoView({ behavior: reducedMotion ? 'instant' : 'smooth', block: 'start' });
        });
    });
    const dismiss = panel.querySelector('[data-coming-soon-dismiss]');
    if (!dismiss) return;
    dismiss.hidden = false;
    dismiss.addEventListener('click', () => {
        panel.hidden = true;
        try { localStorage.setItem(key, 'dismissed'); } catch (_) { /* Current page only. */ }
    });
})();
