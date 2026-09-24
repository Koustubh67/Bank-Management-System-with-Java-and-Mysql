// Loan application form: live EMI and affordability at the fixed or floating rate the customer picks.
// The maths comes from calc.js (the same reducing-balance schedule the bank uses).
(function () {
    const form = document.querySelector('[data-loan-apply]');
    if (!form || !window.JBCalc) return;
    const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
    const inr2 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const q = (s) => form.querySelector(s);

    function update() {
        const type = q('input[name=type]:checked');
        if (!type) return;
        const d = type.dataset;
        q('[data-limits]').textContent = `${inr.format(d.min)} – ${inr.format(d.max)}`;
        q('[data-tenure-limits]').textContent = `${d.minm} – ${d.maxm}`;
        form.querySelectorAll('[data-rate-of]').forEach((el) => {
            el.textContent = Number(el.dataset.rateOf === 'FIXED' ? d.fixed : d.floating).toFixed(2) + '% p.a.';
        });
        const rateType = q('input[name=rateType]:checked');
        const rate = Number(rateType && rateType.value === 'FIXED' ? d.fixed : d.floating);
        const p = Number(form.amount.value), n = Number(form.months.value), income = Number(form.monthlyIncome.value);
        const verdict = q('[data-live-verdict]');
        if (!p || !n || n < 1) {
            ['[data-live-emi]', '[data-live-interest]', '[data-live-foir]'].forEach((s) => { q(s).textContent = '—'; });
            verdict.textContent = '';
            return;
        }
        const s = window.JBCalc.schedule(p, rate, n);
        q('[data-live-emi]').textContent = inr2.format(s.emi);
        q('[data-live-interest]').textContent = inr.format(s.interest) + ` at ${rate.toFixed(2)}%`;
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
})();
