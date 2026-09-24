// Loans: the public EMI calculator and the live EMI / affordability check on the application form.
// The maths matches EmiCalculator on the server (reducing balance, interest rounded to the paisa each month).
(function () {
    const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
    const inr2 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const round2 = (v) => Math.round(v * 100) / 100;
    const DEFAULTS = { HOME: [3000000, 240], CAR: [800000, 60], PERSONAL: [500000, 36], EDUCATION: [1000000, 84],
        TWO_WHEELER: [100000, 24], GOLD: [200000, 12] };

    function emi(p, rate, n) {
        const r = rate / 1200;
        if (!r) return round2(p / n);
        const pw = Math.pow(1 + r, n);
        return round2(p * r * pw / (pw - 1));
    }

    /** Year-by-year totals and total interest, month by month like the bank's schedule. */
    function schedule(p, rate, n) {
        const r = rate / 1200, e = emi(p, rate, n);
        let bal = round2(p), interest = 0;
        const years = [];
        for (let m = 1; m <= n; m++) {
            const i = round2(bal * r);
            let pr = round2(e - i);
            if (m === n || pr > bal) pr = bal;
            bal = round2(bal - pr);
            interest += i;
            const y = Math.ceil(m / 12);
            years[y - 1] = years[y - 1] || { year: y, principal: 0, interest: 0, balance: 0 };
            years[y - 1].principal += pr;
            years[y - 1].interest += i;
            years[y - 1].balance = bal;
        }
        return { emi: e, interest: round2(interest), years };
    }

    const tenureLabel = (m) => {
        const y = Math.floor(m / 12), r = m % 12;
        return [y ? y + (y === 1 ? ' yr' : ' yrs') : '', r ? r + ' mo' : ''].filter(Boolean).join(' ');
    };

    // ---------- Public EMI calculator ----------
    const calc = document.querySelector('[data-emi-calc]');
    if (calc) {
        const input = (k) => calc.querySelector(`[data-in="${k}"]`);
        const out = (k, v) => { calc.querySelector(`[data-out="${k}"]`).textContent = v; };
        const tabs = [...calc.querySelectorAll('[data-loan-type]')];

        function selectType(tab) {
            tabs.forEach((t) => {
                t.classList.toggle('on', t === tab);
                t.setAttribute('aria-selected', String(t === tab));
            });
            const d = tab.dataset, amount = input('amount'), months = input('months'), rate = input('rate');
            const max = Number(d.max);
            amount.min = d.min;
            amount.max = d.max;
            amount.step = max >= 10000000 ? 100000 : max >= 1000000 ? 10000 : 1000;
            months.min = d.minm;
            months.max = d.maxm;
            months.step = Number(d.maxm) >= 120 ? 12 : 1;
            const [a, m] = DEFAULTS[d.loanType] || [Number(d.min) * 4, Number(d.minm)];
            amount.value = a;
            months.value = m;
            rate.value = d.rate;
            update();
        }

        function update() {
            const p = Number(input('amount').value), rate = Number(input('rate').value), n = Number(input('months').value);
            const s = schedule(p, rate, n);
            out('amount', inr.format(p));
            out('rate', rate.toFixed(2) + '%');
            out('months', tenureLabel(n));
            out('emi', inr2.format(s.emi));
            out('principal', inr.format(p));
            out('interest', inr.format(s.interest));
            out('total', inr.format(p + s.interest));
            calc.querySelector('[data-bar]').style.width = (p / (p + s.interest) * 100).toFixed(1) + '%';
            calc.querySelector('[data-emi-years]').innerHTML = s.years.map((y) =>
                `<tr><td>Year ${y.year}</td><td class="num">${inr.format(y.principal)}</td>`
                + `<td class="num">${inr.format(y.interest)}</td><td class="num">${inr.format(y.balance)}</td></tr>`).join('');
        }

        tabs.forEach((t) => t.addEventListener('click', () => selectType(t)));
        calc.querySelectorAll('input[type=range]').forEach((r) => r.addEventListener('input', update));
        selectType(tabs.find((t) => t.classList.contains('on')) || tabs[0]);
    }

    // ---------- Application form: live EMI and affordability ----------
    const form = document.querySelector('[data-loan-apply]');
    if (form) {
        const q = (s) => form.querySelector(s);
        function update() {
            const type = q('input[name=type]:checked');
            if (!type) return;
            const d = type.dataset;
            q('[data-limits]').textContent = `${inr.format(d.min)} – ${inr.format(d.max)}`;
            q('[data-tenure-limits]').textContent = `${d.minm} – ${d.maxm}`;
            const p = Number(form.amount.value), n = Number(form.months.value), income = Number(form.monthlyIncome.value);
            const verdict = q('[data-live-verdict]');
            if (!p || !n || n < 1) {
                ['[data-live-emi]', '[data-live-interest]', '[data-live-foir]'].forEach((s) => { q(s).textContent = '—'; });
                verdict.textContent = '';
                return;
            }
            const s = schedule(p, Number(d.rate), n);
            q('[data-live-emi]').textContent = inr2.format(s.emi);
            q('[data-live-interest]').textContent = inr.format(s.interest) + ` at ${d.rate}%`;
            if (income > 0) {
                const foir = s.emi / income * 100;
                q('[data-live-foir]').textContent = foir.toFixed(1) + '%';
                verdict.textContent = foir <= 50 ? '✓ Looks affordable: EMI is within 50% of your income'
                    : '⚠ EMI is more than 50% of your income. Try a smaller amount or a longer tenure';
                verdict.className = 'small ' + (foir <= 50 ? 'profit' : 'loss');
            } else {
                q('[data-live-foir]').textContent = '—';
                verdict.textContent = 'Enter your monthly income to check affordability';
                verdict.className = 'small muted';
            }
        }
        form.addEventListener('input', update);
        form.addEventListener('change', update);
        update();
    }
})();
