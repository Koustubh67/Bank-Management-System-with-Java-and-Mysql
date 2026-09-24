// Public Invest pages: search / filter / sort the fund list, and the "what if you had invested" calculator.
(function () {
    const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });

    // ---------- Fund explorer ----------
    const explorer = document.querySelector('[data-fund-explorer]');
    if (explorer) {
        const rows = [...explorer.querySelectorAll('.fund-row')];
        const body = explorer.querySelector('[data-rows]');
        const search = explorer.querySelector('[data-search]');
        const sort = explorer.querySelector('[data-sort]');
        const chips = [...explorer.querySelectorAll('[data-group]')].filter((b) => b.tagName === 'BUTTON');
        const empty = explorer.querySelector('[data-empty]');
        let group = 'ALL';

        function apply() {
            const q = search.value.trim().toLowerCase();
            const key = sort.value;
            let visible = 0;
            rows.sort((a, b) => key === 'name'
                ? a.dataset.name.localeCompare(b.dataset.name)
                : Number(b.dataset[key]) - Number(a.dataset[key]));
            rows.forEach((row) => {
                const show = (group === 'ALL' || row.dataset.group === group) && (!q || row.dataset.name.includes(q));
                row.hidden = !show;
                if (show) visible++;
                body.appendChild(row);
            });
            empty.hidden = visible > 0;
        }
        function setGroup(g) {
            group = g;
            chips.forEach((c) => c.classList.toggle('on', c.dataset.group === g));
            apply();
        }
        chips.forEach((c) => c.addEventListener('click', () => setGroup(c.dataset.group)));
        search.addEventListener('input', apply);
        sort.addEventListener('change', apply);
        // Whole row is clickable
        rows.forEach((row) => row.addEventListener('click', (e) => {
            if (!e.target.closest('a')) window.location.href = row.dataset.href;
        }));
        // Collection cards filter the list and scroll to it
        document.querySelectorAll('[data-filter-group]').forEach((card) => card.addEventListener('click', () => {
            setGroup(card.dataset.filterGroup);
            explorer.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }));
        apply();
    }

    // ---------- What if you had invested ----------
    const bt = document.querySelector('[data-backtest]');
    if (bt) {
        const amount = bt.querySelector('[data-bt-amount]');
        const years = bt.querySelector('[data-bt-years]');
        const note = bt.querySelector('[data-bt-note]');
        const out = (k) => bt.querySelector(`[data-bt="${k}"]`);
        let timer;
        function run() {
            const mode = bt.querySelector('input[name=bt-mode]:checked').value;
            const url = `${bt.dataset.src}?mode=${mode}&amount=${encodeURIComponent(amount.value)}&years=${years.value}`;
            bt.classList.add('loading');
            fetch(url, { headers: { Accept: 'application/json' } })
                .then((r) => r.json().then((j) => (r.ok ? j : Promise.reject(j.error))))
                .then((r) => {
                    const gain = Number(r.gain);
                    out('invested').textContent = inr.format(r.invested);
                    out('value').textContent = inr.format(r.value);
                    out('gain').textContent = `${gain >= 0 ? '+' : '−'}${inr.format(Math.abs(gain))} (${Math.abs(r.gainPercent)}%)`;
                    out('gain').className = gain >= 0 ? 'profit' : 'loss';
                    note.textContent = (mode === 'sip' ? `${r.years * 12} monthly instalments` : 'One investment')
                        + ` from ${new Date(r.from).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' })}, `
                        + `${Number(r.units).toFixed(3)} units, valued at the NAV of `
                        + new Date(r.to).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) + '.';
                })
                .catch((msg) => {
                    ['invested', 'value', 'gain'].forEach((k) => { out(k).textContent = '—'; });
                    note.textContent = typeof msg === 'string' ? msg : 'Could not calculate right now';
                })
                .finally(() => bt.classList.remove('loading'));
        }
        bt.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(run, 300); });
        bt.addEventListener('change', () => { clearTimeout(timer); timer = setTimeout(run, 100); });
        run();
    }
})();
