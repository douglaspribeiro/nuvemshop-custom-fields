(() => {
    const panel = document.querySelector('[data-merchant-news]');
    if (!panel) return;
    const items = Array.from(panel.querySelectorAll('[data-news-id]'));
    const key = item => `ncf-news:${panel.dataset.storeId}:${item.dataset.newsId}`;
    for (const item of items) {
        try { item.hidden = localStorage.getItem(key(item)) === 'dismissed'; }
        catch { /* The card still works when browser storage is unavailable. */ }
    }
    panel.hidden = items.every(item => item.hidden);
    const dismiss = panel.querySelector('[data-dismiss-news]');
    if (!dismiss) return;
    dismiss.hidden = false;
    dismiss.addEventListener('click', () => {
        for (const item of items) {
            try { localStorage.setItem(key(item), 'dismissed'); } catch { /* Dismiss for this page. */ }
        }
        panel.hidden = true;
        document.querySelector('.page-header a')?.focus();
    });
})();
