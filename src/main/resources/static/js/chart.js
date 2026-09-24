// JavaBank interactive investment charts (no library).
//   <div data-chart data-src="/customer/invest/api/portfolio" data-mode="value" data-range="Y1"></div>
// mode "value": portfolio/holding value with the amount invested as a dashed line.
// mode "nav":   a fund's NAV.
// Pages with [data-live] also poll /customer/invest/api/live every minute and animate changed numbers.
(function () {
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
    const inr2 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const fmtDate = (s) => new Date(s + 'T00:00:00').toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
    const RANGES = [['M1', '1M'], ['M6', '6M'], ['Y1', '1Y'], ['Y3', '3Y'], ['Y5', '5Y'], ['ALL', 'All']];
    const NS = 'http://www.w3.org/2000/svg';
    const H = 240, PAD_T = 16, PAD_B = 26;
    const charts = [];

    function el(tag, attrs, parent) {
        const e = document.createElementNS(NS, tag);
        Object.entries(attrs || {}).forEach(([k, v]) => e.setAttribute(k, v));
        if (parent) parent.appendChild(e);
        return e;
    }

    function mount(box) {
        const mode = box.dataset.mode || 'value';
        const state = { box, mode, range: box.dataset.range || 'Y1', points: [], src: box.dataset.src };
        box.classList.add('jbc');
        box.innerHTML = '';

        const head = document.createElement('div');
        head.className = 'jbc-head';
        const change = document.createElement('div');
        change.className = 'jbc-change';
        const tabs = document.createElement('div');
        tabs.className = 'jbc-tabs';
        tabs.setAttribute('role', 'tablist');
        RANGES.forEach(([key, label]) => {
            const b = document.createElement('button');
            b.type = 'button';
            b.textContent = label;
            b.dataset.range = key;
            b.setAttribute('role', 'tab');
            b.className = key === state.range ? 'on' : '';
            b.addEventListener('click', () => { state.range = key; load(state); });
            tabs.appendChild(b);
        });
        head.append(change, tabs);

        const wrap = document.createElement('div');
        wrap.className = 'jbc-plot';
        const svg = el('svg', { class: 'jbc-svg', role: 'img', 'aria-label': mode === 'nav' ? 'NAV chart' : 'Value chart' }, wrap);
        const tip = document.createElement('div');
        tip.className = 'jbc-tip';
        tip.hidden = true;
        wrap.appendChild(tip);
        const legend = document.createElement('div');
        legend.className = 'jbc-legend';
        legend.innerHTML = mode === 'nav'
            ? '<span><i class="ln"></i>NAV (₹)</span>'
            : '<span><i class="ln"></i>Value</span><span><i class="inv"></i>Invested</span>';
        box.append(head, wrap, legend);
        Object.assign(state, { svg, tip, tabs, change, wrap });

        wrap.addEventListener('pointermove', (e) => hover(state, e));
        wrap.addEventListener('pointerleave', () => { tip.hidden = true; state.svg.querySelectorAll('.jbc-cross, .jbc-dot').forEach((n) => n.remove()); });
        if ('ResizeObserver' in window) new ResizeObserver(() => draw(state, false)).observe(wrap);
        charts.push(state);
        load(state);
    }

    function load(state) {
        state.tabs.querySelectorAll('button').forEach((b) => {
            b.classList.toggle('on', b.dataset.range === state.range);
            b.setAttribute('aria-selected', String(b.dataset.range === state.range));
        });
        state.box.classList.add('loading');
        fetch(state.src + '?range=' + state.range, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
            .then((r) => r.ok ? r.json() : r.json().then((j) => Promise.reject(j.error || 'Chart unavailable')))
            .then((points) => {
                state.points = points.map((p) => ({ date: p.date, value: Number(p.value), invested: p.invested == null ? null : Number(p.invested) }));
                state.box.classList.remove('loading');
                draw(state, true);
            })
            .catch((msg) => {
                state.box.classList.remove('loading');
                state.change.textContent = typeof msg === 'string' ? msg : 'Live prices are unavailable right now';
            });
    }

    function scales(state) {
        const w = Math.max(280, state.wrap.clientWidth);
        const pts = state.points;
        let min = Infinity, max = -Infinity;
        pts.forEach((p) => {
            min = Math.min(min, p.value, p.invested ?? p.value);
            max = Math.max(max, p.value, p.invested ?? p.value);
        });
        if (min === max) { min -= 1; max += 1; }
        const pad = (max - min) * 0.08;
        min -= pad; max += pad;
        if (state.mode === 'value' && min < 0) min = 0; // money values never go below zero
        const x = (i) => pts.length === 1 ? w / 2 : (i / (pts.length - 1)) * w;
        const y = (v) => PAD_T + (1 - (v - min) / (max - min)) * (H - PAD_T - PAD_B);
        return { w, x, y, min, max };
    }

    function draw(state, animate) {
        const pts = state.points;
        const svg = state.svg;
        svg.innerHTML = '';
        if (!pts.length) {
            state.change.textContent = 'No data for this period yet';
            return;
        }
        if (pts.length < 2) {
            // Bought today: nothing to draw yet. Mutual fund NAVs are published once a day, so the line starts tomorrow.
            state.box.classList.add('just-bought');
            state.change.innerHTML = `<b>${inr2.format(pts[0].value)}</b> <span>on ${fmtDate(pts[0].date)} · the chart fills in `
                + `as new NAVs are published (once every business day)</span>`;
            return;
        }
        state.box.classList.remove('just-bought');
        const { w, x, y, min, max } = scales(state);
        svg.setAttribute('viewBox', `0 0 ${w} ${H}`);
        const first = pts[0], last = pts[pts.length - 1];
        const up = state.mode === 'nav' ? last.value >= first.value : last.value >= (last.invested ?? first.value);
        state.box.classList.toggle('down', !up);

        // Gridlines with value labels
        for (let i = 0; i <= 3; i++) {
            const v = min + (max - min) * (i / 3);
            const gy = y(v);
            el('line', { x1: 0, x2: w, y1: gy, y2: gy, class: 'jbc-grid' }, svg);
            el('text', { x: 4, y: gy - 4, class: 'jbc-axis' }, svg).textContent = state.mode === 'nav' ? v.toFixed(2) : inr.format(v);
        }
        el('text', { x: 4, y: H - 6, class: 'jbc-axis' }, svg).textContent = fmtDate(first.date);
        el('text', { x: w - 4, y: H - 6, class: 'jbc-axis', 'text-anchor': 'end' }, svg).textContent = fmtDate(last.date);

        const defs = el('defs', {}, svg);
        const grad = el('linearGradient', { id: 'jbcg' + charts.indexOf(state), x1: 0, x2: 0, y1: 0, y2: 1 }, defs);
        el('stop', { offset: '0%', class: 'jbc-stop-top' }, grad);
        el('stop', { offset: '100%', class: 'jbc-stop-bottom' }, grad);

        const line = pts.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ');
        el('path', { d: `${line} L${x(pts.length - 1)},${H - PAD_B} L0,${H - PAD_B} Z`, fill: `url(#jbcg${charts.indexOf(state)})`, class: 'jbc-area' }, svg);
        if (state.mode === 'value' && pts.some((p) => p.invested != null)) {
            const inv = pts.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p.invested).toFixed(1)}`).join(' ');
            el('path', { d: inv, class: 'jbc-invested' }, svg);
        }
        const path = el('path', { d: line, class: 'jbc-line' }, svg);
        el('circle', { cx: x(pts.length - 1), cy: y(last.value), r: 4.5, class: 'jbc-last' }, svg);
        if (animate && !reduceMotion && path.getTotalLength) {
            const len = path.getTotalLength();
            path.style.strokeDasharray = len;
            path.style.strokeDashoffset = len;
            path.getBoundingClientRect();
            path.style.transition = 'stroke-dashoffset 1s ease';
            path.style.strokeDashoffset = '0';
        }

        // Headline change for the selected range
        const label = RANGES.find(([k]) => k === state.range)[1];
        if (state.mode === 'nav') {
            const pct = ((last.value - first.value) / first.value) * 100;
            state.change.innerHTML = `<b class="${pct >= 0 ? 'up' : 'dn'}">${pct >= 0 ? '▲' : '▼'} ${Math.abs(pct).toFixed(2)}%</b> <span>over ${label === 'All' ? 'all time' : label}</span>`;
        } else {
            const gain = last.value - last.invested;
            const pct = last.invested ? (gain / last.invested) * 100 : 0;
            state.change.innerHTML = `<b class="${gain >= 0 ? 'up' : 'dn'}">${gain >= 0 ? '▲ +' : '▼ −'}${inr2.format(Math.abs(gain))} (${Math.abs(pct).toFixed(2)}%)</b> <span>total returns · as of ${fmtDate(last.date)}</span>`;
        }
    }

    function hover(state, e) {
        const pts = state.points;
        if (!pts.length) return;
        const rect = state.wrap.getBoundingClientRect();
        const { w, x, y } = scales(state);
        const px = (e.clientX - rect.left) * (w / rect.width);
        const i = Math.max(0, Math.min(pts.length - 1, Math.round(pts.length === 1 ? 0 : (px / w) * (pts.length - 1))));
        const p = pts[i];
        state.svg.querySelectorAll('.jbc-cross, .jbc-dot').forEach((n) => n.remove());
        el('line', { x1: x(i), x2: x(i), y1: PAD_T, y2: H - PAD_B, class: 'jbc-cross' }, state.svg);
        el('circle', { cx: x(i), cy: y(p.value), r: 5, class: 'jbc-dot' }, state.svg);
        let html = `<small>${fmtDate(p.date)}</small>`;
        if (state.mode === 'nav') {
            html += `<b>NAV ₹${p.value.toFixed(4)}</b>`;
        } else {
            const g = p.value - p.invested;
            html += `<b>${inr2.format(p.value)}</b><span>Invested ${inr.format(p.invested)}</span>`
                + `<span class="${g >= 0 ? 'up' : 'dn'}">${g >= 0 ? '+' : '−'}${inr2.format(Math.abs(g))} (${p.invested ? Math.abs(g / p.invested * 100).toFixed(2) : '0.00'}%)</span>`;
        }
        state.tip.innerHTML = html;
        state.tip.hidden = false;
        const left = (x(i) / w) * rect.width;
        state.tip.style.left = Math.min(Math.max(left, 70), rect.width - 70) + 'px';
    }

    document.querySelectorAll('[data-chart]').forEach(mount);

    // ---------- Live updates ----------
    const liveBadge = document.querySelector('[data-live]');
    if (!liveBadge) return;
    const lastValues = {};

    function flash(node, up) {
        node.classList.remove('tick-up', 'tick-down');
        void node.offsetWidth;
        node.classList.add(up ? 'tick-up' : 'tick-down');
    }

    function setMoney(selector, value, key) {
        document.querySelectorAll(selector).forEach((node) => {
            node.textContent = inr2.format(value);
            if (key in lastValues && lastValues[key] !== value) flash(node, value > lastValues[key]);
        });
        lastValues[key] = value;
    }

    function setGain(selector, gain, pct) {
        document.querySelectorAll(selector).forEach((node) => {
            node.textContent = `${gain >= 0 ? '+ ' : '− '}${inr2.format(Math.abs(gain))} (${pct}%)`;
            node.classList.toggle('profit', gain >= 0);
            node.classList.toggle('loss', gain < 0);
        });
    }

    function poll() {
        fetch('/customer/invest/api/live', { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
            .then((r) => r.ok ? r.json() : Promise.reject())
            .then((live) => {
                const changed = 'portfolio' in lastValues && lastValues.portfolio !== Number(live.value);
                setMoney('[data-live-value="portfolio"]', Number(live.value), 'portfolio');
                setGain('[data-live-gain="portfolio"]', Number(live.gain), live.gainPercent);
                let navDate = null;
                live.holdings.forEach((h) => {
                    setMoney(`[data-live-value="${h.id}"]`, Number(h.value), 'h' + h.id);
                    setGain(`[data-live-gain="${h.id}"]`, Number(h.gain), h.gainPercent);
                    document.querySelectorAll(`[data-live-day="${h.id}"]`).forEach((node) => {
                        if (h.change1d == null) return;
                        const c = Number(h.change1d);
                        node.textContent = `${c >= 0 ? '▲' : '▼'} ${Math.abs(c).toFixed(2)}% today`;
                        node.className = 'day-chip ' + (c >= 0 ? 'up' : 'dn');
                    });
                    if (h.navDate && (!navDate || h.navDate > navDate)) navDate = h.navDate;
                });
                const time = new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
                liveBadge.innerHTML = `<i></i> Live · checked ${time}` + (navDate ? ` · latest NAV ${fmtDate(navDate)}` : '');
                if (changed) charts.forEach((c) => { if (c.mode === 'value') load(c); });
            })
            .catch(() => { liveBadge.innerHTML = '<i class="off"></i> Live prices unavailable, retrying…'; });
    }
    poll();
    setInterval(poll, 60000);
})();
