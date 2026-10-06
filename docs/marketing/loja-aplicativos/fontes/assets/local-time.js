(function () {
    'use strict';

    var locale = document.documentElement.lang || navigator.language || 'pt-BR';
    var formatters = {};

    document.querySelectorAll('[data-local-time]').forEach(function (element) {
        var timestamp = element.getAttribute('data-local-time');
        // Only convert instants with an explicit offset. Calendar dates are not instants.
        if (!timestamp || !/T.*(?:Z|[+-]\d{2}:\d{2})$/.test(timestamp)) return;
        var instant = new Date(timestamp);
        if (!Number.isFinite(instant.getTime())) return;
        var format = element.getAttribute('data-time-format') || 'datetime';
        try {
            if (!formatters[format]) {
                var options = { year: 'numeric', month: '2-digit', day: '2-digit' };
                if (format !== 'date') {
                    options.hour = '2-digit'; options.minute = '2-digit'; options.hourCycle = 'h23';
                    if (format === 'seconds') options.second = '2-digit';
                }
                formatters[format] = new Intl.DateTimeFormat(locale, options);
            }
            var parts = {};
            formatters[format].formatToParts(instant).forEach(function (part) { parts[part.type] = part.value; });
            var rendered = parts.day + '/' + parts.month + '/' + parts.year;
            if (format !== 'date') rendered += ' ' + parts.hour + ':' + parts.minute;
            if (format === 'seconds') rendered += ':' + parts.second;
            var fallback = element.getAttribute('data-time-fallback');
            if (fallback) {
                if (element.textContent.indexOf(fallback) < 0) return;
                element.textContent = element.textContent.replace(fallback, rendered);
            } else {
                element.textContent = rendered;
            }
            element.title = formatters[format].resolvedOptions().timeZone;
        } catch (_) {
            // Preserve the server-rendered fallback when Intl is unavailable.
        }
    });
}());
