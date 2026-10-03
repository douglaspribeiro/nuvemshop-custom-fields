import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';
import { describe, expect, it } from 'vitest';

const script = readFileSync(new URL('../../../resources/static/assets/local-time.js', import.meta.url), 'utf8');

function render(timeZone: string, body: string, locale = 'pt-BR') {
    const dom = new JSDOM(`<html lang="${locale}"><body>${body}</body></html>`, { runScripts: 'outside-only' });
    const OriginalFormatter = dom.window.Intl.DateTimeFormat;
    dom.window.Intl.DateTimeFormat = class extends OriginalFormatter {
        constructor(locales?: Intl.LocalesArgument, options?: Intl.DateTimeFormatOptions) {
            super(locales, { ...options, timeZone });
        }
    } as unknown as typeof Intl.DateTimeFormat;
    dom.window.eval(script);
    return dom;
}

function text(timeZone: string, instant: string, format = 'datetime') {
    const dom = render(timeZone, `<time data-local-time="${instant}" data-time-format="${format}">fallback</time>`);
    const result = dom.window.document.querySelector('time')!.textContent;
    dom.window.close();
    return result;
}

describe('browser local timestamps', () => {
    it('displays UTC support timestamps in GMT-3', () => {
        expect(text('America/Sao_Paulo', '2026-10-03T15:30:00Z')).toBe('03/10/2026 12:30');
    });
    it('uses the previous calendar day when UTC crosses local midnight', () => {
        expect(text('America/Sao_Paulo', '2026-10-03T01:30:00Z')).toBe('02/10/2026 22:30');
        expect(text('America/Sao_Paulo', '2026-10-03T01:30:00Z', 'date')).toBe('02/10/2026');
    });
    it('uses the next calendar day in positive offsets', () => {
        expect(text('Asia/Tokyo', '2026-10-03T23:30:00Z')).toBe('04/10/2026 08:30');
    });
    it('observes daylight saving rules', () => {
        expect(text('America/New_York', '2026-07-01T12:00:00Z')).toBe('01/07/2026 08:00');
        expect(text('America/New_York', '2026-01-01T12:00:00Z')).toBe('01/01/2026 07:00');
    });
    it('preserves translated surrounding text and formats seconds', () => {
        const dom = render('America/Sao_Paulo', '<p data-local-time="2026-10-03T15:30:45Z" data-time-format="seconds" data-time-fallback="03/10/2026 15:30:45">Alterado por admin em 03/10/2026 15:30:45.</p>');
        expect(dom.window.document.querySelector('p')!.textContent).toBe('Alterado por admin em 03/10/2026 12:30:45.');
        expect(dom.window.document.querySelector('p')!.title).toBe('America/Sao_Paulo');
        dom.window.close();
    });
    it('accepts explicit offsets and fractional seconds', () => {
        expect(text('America/Sao_Paulo', '2026-10-03T12:30:00.123456789-03:00')).toBe('03/10/2026 12:30');
    });
    it('leaves calendar dates, unzoned timestamps and invalid values intact', () => {
        for (const value of ['', 'invalid', '2026-10-03', '2026-10-03T15:30:00', '2026-99-03T15:30:00Z']) {
            expect(text('America/Sao_Paulo', value)).toBe('fallback');
        }
    });
    it('keeps the readable fallback when Intl fails', () => {
        const dom = new JSDOM('<time data-local-time="2026-10-03T15:30:00Z">03/10/2026 15:30 UTC</time>', { runScripts: 'outside-only' });
        dom.window.Intl.DateTimeFormat = class { constructor() { throw new Error('Intl unavailable'); } } as unknown as typeof Intl.DateTimeFormat;
        dom.window.eval(script);
        expect(dom.window.document.querySelector('time')!.textContent).toBe('03/10/2026 15:30 UTC');
        dom.window.close();
    });
});
