// EMI payment pages: payment method tabs, card number formatting, the 5-minute countdown, and waiting for a UPI
// payment. The server enforces the 5 minutes and every rule; this only makes the pages feel like a payment app.
(function () {
    // ---------- Payment method tabs (UPI / card / savings account) ----------
    const tabs = [...document.querySelectorAll('[data-pay-tab]')];
    if (tabs.length) {
        const names = tabs.map((t) => t.dataset.payTab);
        const show = (name) => {
            tabs.forEach((t) => {
                const on = t.dataset.payTab === name;
                t.classList.toggle('on', on);
                t.setAttribute('aria-selected', String(on));
                document.getElementById('pm-' + t.dataset.payTab).hidden = !on;
            });
        };
        tabs.forEach((t) => t.addEventListener('click', () => {
            show(t.dataset.payTab);
            history.replaceState(null, '', '#' + t.dataset.payTab);
        }));
        const wanted = location.hash.slice(1);
        show(names.includes(wanted) ? wanted : names[0]);
    }

    // ---------- Card number in groups of 4, with the card network ----------
    const network = (d) => {
        if (/^3[47]/.test(d)) return 'Amex';
        if (/^4/.test(d)) return 'Visa';
        const four = Number(d.slice(0, 4));
        if (/^5[1-5]/.test(d) || (four >= 2221 && four <= 2720)) return 'Mastercard';
        if (/^(60|65|81|82|508)/.test(d)) return 'RuPay';
        return '';
    };
    const number = document.querySelector('[data-card-number]');
    if (number) {
        const badge = document.querySelector('[data-card-network]');
        const format = () => {
            const d = number.value.replace(/\D/g, '').slice(0, 19);
            number.value = d.replace(/(\d{4})(?=\d)/g, '$1 ');
            badge.textContent = network(d);
        };
        number.addEventListener('input', format);
        format();
    }
    const expiry = document.querySelector('[data-card-expiry]');
    if (expiry) {
        expiry.addEventListener('input', (e) => {
            const d = expiry.value.replace(/\D/g, '').slice(0, 4);
            expiry.value = d.length > 2 || (d.length === 2 && e.inputType !== 'deleteContentBackward') ? d.slice(0, 2) + '/' + d.slice(2) : d;
        });
    }

    // ---------- 5-minute countdown ----------
    document.querySelectorAll('[data-countdown]').forEach((timer) => {
        const session = timer.closest('[data-pay-session]');
        const label = timer.querySelector('span');
        const total = 300;
        const end = Date.now() + Number(timer.dataset.countdown) * 1000;
        const tick = () => {
            const left = Math.max(0, Math.round((end - Date.now()) / 1000));
            label.textContent = Math.floor(left / 60) + ':' + String(left % 60).padStart(2, '0');
            timer.style.setProperty('--p', (left / total * 100).toFixed(1));
            timer.classList.toggle('low', left <= 60);
            if (left === 0) {
                clearInterval(id);
                session.classList.add('expired');
                session.querySelectorAll('button[type=submit], input').forEach((el) => { el.disabled = true; });
                setTimeout(() => location.reload(), 1500); // the server shows the expired page
            }
        };
        const id = setInterval(tick, 1000);
        tick();
    });

    // ---------- UPI: ask the bank every 3 seconds whether the money has arrived ----------
    const poll = document.querySelector('[data-poll]');
    if (poll) {
        const check = () => fetch(poll.dataset.poll, { headers: { Accept: 'application/json' } })
            .then((r) => (r.ok ? r.json() : null))
            .then((s) => { if (s && s.status !== 'PENDING') location.reload(); })
            .catch(() => { /* network hiccup: try again next time */ });
        setInterval(check, 3000);
    }

    // ---------- No double payments from double clicks ----------
    document.querySelectorAll('form[data-processing]').forEach((form) => {
        form.addEventListener('submit', () => {
            const button = form.querySelector('button[type=submit]');
            if (button) {
                setTimeout(() => { button.disabled = true; button.textContent = form.dataset.processing; }, 0);
            }
        });
    });
})();
