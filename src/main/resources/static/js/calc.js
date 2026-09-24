// JavaBank calculators: EMI (fixed or floating, with "what if the repo rate changes"), loan eligibility, SIP, lump sum
// and fixed deposit. Every slider has a box where you can type an exact value (e.g. 12,50,000 or 15L or 1.2Cr).
// The EMI maths matches EmiCalculator on the server: reducing balance, interest rounded to the paisa each month, and
// on a floating-rate reset the EMI is recalculated on the balance still owed so the loan ends on the same date.
(function () {
    const round2 = (v) => Math.round(v * 100) / 100;
    const inr0 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
    const inr2 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const grouped = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });

    // ---------------------------------------------------------------- maths

    function emi(p, rate, n) {
        if (p <= 0 || n <= 0) return 0;
        const r = rate / 1200;
        if (!r) return round2(p / n);
        const pw = Math.pow(1 + r, n);
        return round2(p * r * pw / (pw - 1));
    }

    /**
     * Month-by-month repayment. With `change` ({ delta, from }) the rate moves by `delta` from month `from`, and the
     * EMI for the months left is recalculated on the balance still owed, exactly like a floating-rate reset.
     */
    function schedule(p, rate, n, change) {
        let r = rate, e = emi(p, r, n), bal = round2(p), interest = 0, emiAfter = null;
        const years = [];
        for (let m = 1; m <= n; m++) {
            if (change && change.delta && m === change.from) {
                r = Math.max(0, rate + change.delta);
                e = emi(bal, r, n - m + 1);
                emiAfter = e;
            }
            const i = round2(bal * r / 1200);
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
        return { emi: emi(p, rate, n), emiAfter, interest: round2(interest), years };
    }

    const tenureLabel = (m) => {
        const y = Math.floor(m / 12), r = m % 12;
        return [y ? y + (y === 1 ? ' yr' : ' yrs') : '', r ? r + ' mo' : ''].filter(Boolean).join(' ') || '0 mo';
    };

    // ---------------------------------------------------------------- slider + typed value

    /**
     * Connects a slider to its text box. The box holds the real value: what you type is used exactly (the slider
     * only shows roughly where it is); dragging the slider writes its value into the box. Out-of-range or unreadable
     * input shows a hint and is corrected when you leave the box or press Enter.
     */
    function field(root, name, onChange) {
        const wrap = root.querySelector(`[data-field="${name}"]`);
        const box = wrap.querySelector('.rv-in');
        const slider = wrap.querySelector('input[type=range]');
        const hint = wrap.querySelector('.rv-hint');
        const unitLabel = wrap.querySelector('[data-unit-label]');
        const unitButtons = [...wrap.querySelectorAll('[data-unit]')];
        let unit = 'y';                     // tenure fields: shown in years or months (the value is always months)
        let value = 0, hardMin = null, hardMax = null;
        const kind = () => wrap.dataset.kind;
        const limits = () => ({ min: hardMin ?? Number(slider.min), max: hardMax ?? Number(slider.max) });

        function format(v) {
            switch (kind()) {
                case 'money': return grouped.format(Math.round(v));
                case 'rate': return v.toFixed(2);
                case 'percent': return String(round2(v));
                case 'delta': return (v > 0 ? '+' : '') + v.toFixed(2);
                case 'tenure': return unit === 'y' ? String(round2(v / 12)) : String(v);
                default: return String(v);
            }
        }
        function describe(v) {
            switch (kind()) {
                case 'money': return inr0.format(v);
                case 'rate': case 'percent': case 'delta': return format(v) + '%';
                case 'tenure': return tenureLabel(v);
                case 'years': return v + (v === 1 ? ' yr' : ' yrs');
                default: return String(v);
            }
        }
        function parse(text) {
            const t = String(text).trim().toLowerCase().replace(/[,\s₹%]/g, '').replace('−', '-');
            if (kind() === 'money') {
                const m = t.match(/^(\d+(?:\.\d+)?|\.\d+)(k|l|lac|lakh|lakhs|cr|crore|crores)?$/);
                if (!m) return NaN;
                const mult = !m[2] ? 1 : m[2] === 'k' ? 1e3 : m[2].startsWith('c') ? 1e7 : 1e5;
                return Math.round(Number(m[1]) * mult);
            }
            if (!/^[+-]?(\d+\.?\d*|\.\d+)$/.test(t)) return NaN;
            const v = Number(t);
            switch (kind()) {
                case 'tenure': return Math.round(unit === 'y' ? v * 12 : v);
                case 'years': case 'count': return Math.round(v);
                default: return round2(v);
            }
        }
        function setHint(text) {
            hint.textContent = text || '';
            wrap.classList.toggle('bad', Boolean(text));
        }
        // Size the box to its text: digits are about 1ch wide, commas and dots about half that
        function fit() {
            const digits = box.value.replace(/[^0-9]/g, '').length;
            box.style.width = Math.max(2.5, digits + (box.value.length - digits) * 0.45 + 0.3) + 'ch';
        }
        function render() {
            box.value = format(value);
            fit();
            slider.value = value;
            if (unitLabel) unitLabel.textContent = value === 1 ? 'yr' : 'yrs';
            unitButtons.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.unit === unit)));
        }
        function commit() {
            const v = parse(box.value), { min, max } = limits();
            if (!Number.isNaN(v)) value = Math.min(max, Math.max(min, v));
            setHint('');
            render();
            onChange();
        }

        slider.addEventListener('input', () => {
            value = Number(slider.value);
            setHint('');
            render();
            onChange();
        });
        box.addEventListener('focus', () => box.select());
        box.addEventListener('input', () => {
            fit();
            const v = parse(box.value), { min, max } = limits();
            if (Number.isNaN(v)) { setHint('Type a number'); return; }
            if (v < min || v > max) { setHint(`Between ${describe(min)} and ${describe(max)}`); return; }
            setHint('');
            value = v;
            slider.value = v;
            if (unitLabel) unitLabel.textContent = v === 1 ? 'yr' : 'yrs';
            onChange();
        });
        box.addEventListener('change', commit);
        box.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') { e.preventDefault(); commit(); }
        });
        unitButtons.forEach((b) => b.addEventListener('click', () => { unit = b.dataset.unit; setHint(''); render(); }));

        return {
            get value() { return value; },
            set(v) { value = v; setHint(''); render(); },
            /** Slider range and step; typed values may go from hardMin to hardMax (default: the slider range). */
            range(min, max, step, hMin, hMax) {
                slider.min = min;
                slider.max = max;
                slider.step = step;
                hardMin = hMin ?? null;
                hardMax = hMax ?? null;
                const l = limits();
                if (value < l.min || value > l.max) value = Math.min(l.max, Math.max(l.min, value));
                render();
            },
            label(text) { wrap.querySelector('[data-label]').textContent = text; },
            kind(k) { wrap.dataset.kind = k; },
        };
    }

    const out = (root, key, text) => {
        const el = root.querySelector(`[data-out="${key}"]`);
        if (el) el.textContent = text;
    };

    function selectTab(tabs, tab) {
        tabs.forEach((t) => {
            t.classList.toggle('on', t === tab);
            t.setAttribute('aria-selected', String(t === tab));
        });
    }

    // ---------------------------------------------------------------- EMI calculator

    const DEFAULTS = { HOME: [3000000, 240], CAR: [800000, 60], PERSONAL: [500000, 36], EDUCATION: [1000000, 84],
        TWO_WHEELER: [100000, 24], GOLD: [200000, 12] };

    document.querySelectorAll('[data-emi-calc]').forEach((calc) => {
        const repo = Number(calc.dataset.repo);
        const tabs = [...calc.querySelectorAll('[data-loan-type]')];
        const f = {};
        ['amount', 'rate', 'tenure', 'delta', 'from'].forEach((k) => { f[k] = field(calc, k, update); });
        f.rate.range(5, 20, 0.05, 1, 30);
        f.delta.range(-2, 3, 0.25, -5, 5);
        f.delta.set(0);
        let product = tabs[0].dataset;
        let rateType = 'FLOATING';

        const offerRate = (type) => Number(type === 'FIXED' ? product.fixed : product.floating);

        function selectType(tab) {
            selectTab(tabs, tab);
            product = tab.dataset;
            const max = Number(product.max), maxm = Number(product.maxm);
            f.amount.range(product.min, product.max, max >= 10000000 ? 100000 : max >= 1000000 ? 10000 : 1000);
            f.tenure.range(product.minm, product.maxm, maxm >= 120 ? 12 : 1);
            const [a, m] = DEFAULTS[product.loanType] || [Number(product.min) * 4, Number(product.minm)];
            f.amount.set(a);
            f.tenure.set(m);
            calc.querySelectorAll('[data-rate-of]').forEach((el) => {
                el.textContent = offerRate(el.dataset.rateOf).toFixed(2) + '% p.a.';
            });
            f.rate.set(offerRate(rateType));
            update();
        }

        function setRateType(type) {
            rateType = type;
            f.rate.set(offerRate(type));
            update();
        }

        function update() {
            const p = f.amount.value, n = f.tenure.value, rate = f.rate.value;
            const years = Math.max(1, Math.ceil(n / 12));
            f.from.range(1, years, 1);
            const from = f.from.value;
            const change = { delta: f.delta.value, from: (from - 1) * 12 + 1 };

            // The selected option, with the rate change applied if it is floating
            const main = schedule(p, rate, n, rateType === 'FLOATING' ? change : null);
            out(calc, 'emi', inr2.format(main.emi));
            out(calc, 'emi-after', main.emiAfter == null ? ''
                : `then ${inr2.format(main.emiAfter)} from year ${from} if the repo rate moves ${f.delta.value > 0 ? '+' : ''}${f.delta.value.toFixed(2)}%`);
            out(calc, 'principal', inr0.format(p));
            out(calc, 'interest', inr0.format(main.interest));
            out(calc, 'total', inr0.format(p + main.interest));
            calc.querySelector('[data-bar]').style.width = (p / (p + main.interest) * 100).toFixed(1) + '%';
            calc.querySelector('[data-emi-years]').innerHTML = main.years.map((y) =>
                `<tr><td>Year ${y.year}</td><td class="num">${inr0.format(y.principal)}</td>`
                + `<td class="num">${inr0.format(y.interest)}</td><td class="num">${inr0.format(y.balance)}</td></tr>`).join('');

            const spread = (Number(product.floating) - repo).toFixed(2);
            const note = calc.querySelector('[data-rate-note]');
            note.textContent = rateType === 'FLOATING'
                ? `Repo rate ${repo.toFixed(2)}% + ${spread}%. Moves with RBI policy: when it changes, the EMIs still to come are recalculated and the loan still ends on time.`
                : `Locked for the whole loan. Costs ${(Number(product.fixed) - Number(product.floating)).toFixed(2)}% more than floating today because the bank takes the risk of rates rising.`;

            // Fixed vs floating, side by side
            const fixedRate = rateType === 'FIXED' ? rate : offerRate('FIXED');
            const floatRate = rateType === 'FLOATING' ? rate : offerRate('FLOATING');
            const fixed = schedule(p, fixedRate, n);
            const floating = schedule(p, floatRate, n, change);
            out(calc, 'ff-fixed-rate', fixedRate.toFixed(2) + '%');
            out(calc, 'ff-fixed-emi', inr2.format(fixed.emi));
            out(calc, 'ff-fixed-interest', inr0.format(fixed.interest));
            out(calc, 'ff-float-rate', floatRate.toFixed(2) + '%');
            out(calc, 'ff-float-emi', inr2.format(floating.emi));
            out(calc, 'ff-float-note', floating.emiAfter == null ? 'If the repo rate stays where it is'
                : `then ${inr2.format(floating.emiAfter)} from year ${from}`);
            out(calc, 'ff-float-interest', inr0.format(floating.interest));
            const diff = fixed.interest - floating.interest;
            out(calc, 'ff-verdict', Math.abs(diff) < 1 ? 'Both options cost about the same.'
                : diff > 0 ? `Floating saves you ${inr0.format(diff)} in interest in this scenario.`
                    : `Fixed saves you ${inr0.format(-diff)} in interest in this scenario.`);
            calc.querySelectorAll('[data-ff]').forEach((c) => {
                c.classList.toggle('picked', c.dataset.ff === rateType);
                c.classList.toggle('cheaper', (c.dataset.ff === 'FIXED') === (diff < 0) && Math.abs(diff) >= 1);
            });
            out(calc, 'ff-breakeven', breakEven(p, n, fixedRate, floatRate, fixed.interest, from));
        }

        /** How far the repo rate would have to rise (from the chosen year) for floating to cost more than fixed. */
        function breakEven(p, n, fixedRate, floatRate, fixedInterest, from) {
            const cost = (d) => schedule(p, floatRate, n, { delta: d, from: (from - 1) * 12 + 1 }).interest;
            if (cost(0) >= fixedInterest) return 'Fixed is cheaper even if the repo rate never changes.';
            if (cost(10) < fixedInterest) return `Floating stays cheaper even if the repo rate rises by 10% from year ${from}.`;
            let lo = 0, hi = 10;
            for (let k = 0; k < 24; k++) {
                const mid = (lo + hi) / 2;
                if (cost(mid) < fixedInterest) lo = mid; else hi = mid;
            }
            return `Break-even: floating stays cheaper unless the repo rate rises by more than ${hi.toFixed(2)}% from year ${from}.`;
        }

        tabs.forEach((t) => t.addEventListener('click', () => selectType(t)));
        calc.querySelectorAll('input[name=emi-rate-type]').forEach((r) => r.addEventListener('change', () => setRateType(r.value)));
        f.from.range(1, 30, 1);
        f.from.set(2);
        selectType(tabs.find((t) => t.classList.contains('on')) || tabs[0]);
    });

    // ---------------------------------------------------------------- SIP / lump sum / FD

    const MODES = {
        sip: {
            amountLabel: 'Monthly investment', amount: [500, 100000, 500, 100, 1000000, 5000],
            years: [1, 40, 1, 1, 50, 10], rateLabel: 'Expected return (p.a.)', rateKind: 'percent', rate: [1, 30, 0.5, 0, 50, 12],
            labels: ['Invested', 'Est. returns', 'Total value'], note: 'For illustration only. Returns are not guaranteed.',
            // Each instalment grows from the day it is invested (annuity due), the way SIP calculators work
            value: (a, y, rate) => {
                const r = rate / 1200, n = y * 12;
                return { invested: a * n, total: r ? a * ((Math.pow(1 + r, n) - 1) / r) * (1 + r) : a * n };
            },
        },
        lumpsum: {
            amountLabel: 'Total investment', amount: [5000, 5000000, 5000, 500, 100000000, 100000],
            years: [1, 40, 1, 1, 50, 10], rateLabel: 'Expected return (p.a.)', rateKind: 'percent', rate: [1, 30, 0.5, 0, 50, 12],
            labels: ['Invested', 'Est. returns', 'Total value'], note: 'For illustration only. Returns are not guaranteed.',
            value: (a, y, rate) => ({ invested: a, total: a * Math.pow(1 + rate / 100, y) }),
        },
        fd: {
            amountLabel: 'Deposit amount', amount: [5000, 2500000, 5000, 1000, 100000000, 100000],
            years: [1, 10, 1, 1, 10, 5], rateLabel: 'Interest rate (p.a.)', rateKind: 'rate', rate: [3, 10, 0.05, 0.1, 15, 7.25],
            labels: ['Deposit', 'Interest earned', 'Maturity value'], note: 'Compounded quarterly, like most bank FDs.',
            value: (a, y, rate) => ({ invested: a, total: a * Math.pow(1 + rate / 400, 4 * y) }),
        },
    };

    document.querySelectorAll('[data-calc]').forEach((calc) => {
        const tabs = [...calc.querySelectorAll('[data-mode]')];
        let mode = calc.dataset.modes.split(',')[0];
        const f = {};
        ['amount', 'years', 'rate'].forEach((k) => { f[k] = field(calc, k, update); });

        function update() {
            const { invested, total } = MODES[mode].value(f.amount.value, f.years.value, f.rate.value);
            out(calc, 'invested', inr0.format(invested));
            out(calc, 'returns', inr0.format(total - invested));
            out(calc, 'total', inr0.format(total));
            calc.querySelector('[data-bar]').style.width = (total > 0 ? invested / total * 100 : 100).toFixed(1) + '%';
        }

        function setMode(m) {
            mode = m;
            const c = MODES[m];
            f.amount.label(c.amountLabel);
            f.rate.label(c.rateLabel);
            f.rate.kind(c.rateKind);
            [['amount', c.amount], ['years', c.years], ['rate', c.rate]].forEach(([k, [min, max, step, hMin, hMax, v]]) => {
                f[k].range(min, max, step, hMin, hMax);
                f[k].set(v);
            });
            calc.querySelectorAll('[data-out-label]').forEach((el) => {
                el.textContent = c.labels[['invested', 'returns', 'total'].indexOf(el.dataset.outLabel)];
            });
            out(calc, 'note', c.note);
            update();
        }

        tabs.forEach((t) => t.addEventListener('click', () => { selectTab(tabs, t); setMode(t.dataset.mode); }));
        setMode(mode);
        // Lets other controls on the page set values, e.g. the FD rate chips on the Tools page
        calc.jbSet = (values) => {
            Object.entries(values).forEach(([k, v]) => f[k].set(v));
            update();
        };
    });

    // ---------------------------------------------------------------- loan eligibility

    document.querySelectorAll('[data-eligibility]').forEach((calc) => {
        const tabs = [...calc.querySelectorAll('[data-loan-type]')];
        const f = {};
        ['income', 'emis', 'rate', 'tenure'].forEach((k) => { f[k] = field(calc, k, update); });
        f.income.range(10000, 500000, 1000, 10000, 100000000);
        f.income.set(80000);
        f.emis.range(0, 200000, 500, 0, 100000000);
        f.emis.set(0);
        f.rate.range(5, 20, 0.05, 1, 30);
        let product = tabs[0].dataset;

        function selectType(tab) {
            selectTab(tabs, tab);
            product = tab.dataset;
            const maxm = Number(product.maxm);
            f.tenure.range(product.minm, product.maxm, maxm >= 120 ? 12 : 1);
            f.tenure.set(Math.min(maxm, maxm >= 240 ? 240 : maxm));
            f.rate.set(Number(product.floating));
            calc.querySelector('[data-apply-link]').href = '/customer/loans/apply?type=' + product.loanType;
            calc.querySelector('[data-callback-link]').href = '/loans?type=' + product.loanType + '#enquire';
            update();
        }

        function update() {
            const half = f.income.value / 2;
            const affordable = Math.max(0, half - f.emis.value);
            const r = f.rate.value / 1200, n = f.tenure.value;
            const max = affordable <= 0 ? 0 : r ? affordable * (1 - Math.pow(1 + r, -n)) / r : affordable * n;
            const productMax = Number(product.max);
            out(calc, 'max', affordable <= 0 ? '₹0' : inr0.format(Math.floor(max / 1000) * 1000));
            out(calc, 'cap', affordable <= 0 ? 'Your current EMIs already take half of your income. Paying one off first would help.'
                : max > productMax ? `JavaBank lends up to ${inr0.format(productMax)} for a ${product.label.toLowerCase()}.`
                    : `over ${tenureLabel(n)} at ${f.rate.value.toFixed(2)}% p.a.`);
            out(calc, 'emi', inr0.format(affordable));
            out(calc, 'half', inr0.format(half));
            out(calc, 'existing', '− ' + inr0.format(f.emis.value));
        }

        tabs.forEach((t) => t.addEventListener('click', () => selectType(t)));
        selectType(tabs[0]);
    });

    // ---------------------------------------------------------------- Tools page tabs (#emi, #sip …)

    const toolTabs = [...document.querySelectorAll('[data-tool]')];
    if (toolTabs.length) {
        const show = (name, focus) => {
            const tab = toolTabs.find((t) => t.dataset.tool === name) || toolTabs[0];
            toolTabs.forEach((t) => {
                const on = t === tab;
                t.classList.toggle('on', on);
                t.setAttribute('aria-selected', String(on));
                document.getElementById('tool-' + t.dataset.tool).hidden = !on;
            });
            if (focus) tab.focus();
        };
        toolTabs.forEach((t) => t.addEventListener('click', (e) => {
            e.preventDefault();
            show(t.dataset.tool);
            history.replaceState(null, '', '#' + t.dataset.tool);
        }));
        addEventListener('hashchange', () => show(location.hash.slice(1)));
        show(location.hash.slice(1));
        document.querySelectorAll('[data-fd-preset]').forEach((b) => b.addEventListener('click', () => {
            document.querySelector(b.dataset.fdPreset).jbSet({ rate: Number(b.dataset.rate), years: Number(b.dataset.years) });
        }));
    }

    window.JBCalc = { emi, schedule };
})();
