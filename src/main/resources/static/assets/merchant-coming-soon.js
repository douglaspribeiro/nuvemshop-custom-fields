(() => {
    const panel = document.querySelector('[data-coming-soon]');
    if (!panel || !panel.dataset.storeId) return;
    const key = `ncf-coming-soon:${panel.dataset.storeId}:${panel.dataset.comingSoon}`;
    try {
        if (localStorage.getItem(key) === 'dismissed') {
            panel.hidden = true;
            return;
        }
    } catch (_) { /* Dismissal still works when browser storage is unavailable. */ }
    const dismiss = panel.querySelector('[data-coming-soon-dismiss]');
    if (!dismiss) return;
    dismiss.hidden = false;
    dismiss.addEventListener('click', () => {
        panel.hidden = true;
        try { localStorage.setItem(key, 'dismissed'); } catch (_) { /* Current page only. */ }
    });
})();
