// JavaPay UPI: phone clock, PIN pad, amount chips, copy buttons and the "paying" overlay.
(function () {
    document.documentElement.classList.add('js');
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const clock = document.getElementById('phoneClock');
    const tick = () => { if (clock) clock.textContent = new Date().toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' }).replace(/\s?[ap]m/i, ''); };
    tick();
    setInterval(tick, 10000);

    // NPCI-style PIN pad writing into the real (hidden) input
    document.querySelectorAll('[data-pinpad]').forEach((pad) => {
        const input = pad.querySelector('.pin-input');
        const dots = pad.querySelectorAll('.pin-dots i');
        const render = () => dots.forEach((d, i) => d.classList.toggle('on', i < input.value.length));
        pad.querySelectorAll('[data-k]').forEach((key) => key.addEventListener('click', () => {
            if (key.dataset.k === 'del') input.value = input.value.slice(0, -1);
            else if (input.value.length < 6) input.value += key.dataset.k;
            render();
        }));
        // Physical keyboard also works
        document.addEventListener('keydown', (e) => {
            if (e.target.matches('input:not(.pin-input), textarea')) return;
            if (/^\d$/.test(e.key) && input.value.length < 6) input.value += e.key;
            else if (e.key === 'Backspace') input.value = input.value.slice(0, -1);
            else if (e.key === 'Enter') { input.form.requestSubmit(); e.preventDefault(); }
            else return;
            render();
        });
        input.form.addEventListener('submit', (e) => {
            if (input.value.length !== 6) {
                e.preventDefault();
                pad.classList.remove('shake');
                void pad.offsetWidth;
                pad.classList.add('shake');
            }
        });
        if (pad.closest('.app').querySelector('.toast.error')) pad.classList.add('shake');
    });

    // Short "paying securely" overlay before the payment is sent
    const payForm = document.querySelector('form[data-upi-pay]');
    if (payForm) {
        payForm.addEventListener('submit', (e) => {
            if (e.defaultPrevented || payForm.dataset.go) return;
            e.preventDefault();
            document.getElementById('paying').hidden = false;
            setTimeout(() => { payForm.dataset.go = '1'; payForm.requestSubmit(); }, reduceMotion ? 0 : 1200);
        });
    }

    document.querySelectorAll('[data-amount-chips] [data-amt]').forEach((chip) => chip.addEventListener('click', () => {
        const amount = chip.closest('form').querySelector('input[name=amount]');
        amount.value = chip.dataset.amt;
        amount.focus();
    }));

    document.querySelectorAll('[data-copy]').forEach((btn) => btn.addEventListener('click', async () => {
        const text = document.querySelector(btn.dataset.copy).textContent.trim();
        try { await navigator.clipboard.writeText(text); btn.textContent = 'Copied ✓'; }
        catch (err) { btn.textContent = 'Select & copy'; }
        setTimeout(() => { btn.textContent = 'Copy'; }, 1800);
    }));
})();
