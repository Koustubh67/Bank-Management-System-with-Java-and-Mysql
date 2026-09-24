// Home page effects: scroll reveal, counting stats, card tilt and header shadow.
(function () {
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    document.documentElement.classList.add('js');

    const header = document.getElementById('siteHeader');
    if (header) {
        const onScroll = () => header.classList.toggle('scrolled', window.scrollY > 10);
        window.addEventListener('scroll', onScroll, { passive: true });
        onScroll();
    }

    // Close the mobile menu after picking a link
    document.querySelectorAll('.main-nav a').forEach(a => a.addEventListener('click', () => {
        const t = document.getElementById('navToggle');
        if (t) t.checked = false;
    }));

    const fmt = new Intl.NumberFormat('en-IN');
    function countUp(el) {
        const target = Number(el.dataset.count) || 0;
        const prefix = el.dataset.prefix || '';
        if (reduceMotion || target === 0) { el.textContent = prefix + fmt.format(target); return; }
        const start = performance.now(), dur = 1400;
        (function tick(now) {
            const p = Math.min((now - start) / dur, 1);
            const eased = 1 - Math.pow(1 - p, 3);
            el.textContent = prefix + fmt.format(Math.round(target * eased));
            if (p < 1) requestAnimationFrame(tick);
        })(start);
    }

    const revealEls = document.querySelectorAll('.reveal');
    const counters = document.querySelectorAll('[data-count]');
    if ('IntersectionObserver' in window && !reduceMotion) {
        const io = new IntersectionObserver((entries) => {
            entries.forEach(e => {
                if (!e.isIntersecting) return;
                const el = e.target;
                const siblings = [...el.parentElement.children].filter(c => c.classList.contains('reveal'));
                el.style.transitionDelay = Math.min(siblings.indexOf(el), 5) * 80 + 'ms';
                el.classList.add('in');
                el.querySelectorAll('[data-count]').forEach(countUp);
                io.unobserve(el);
            });
        }, { threshold: 0.12 });
        revealEls.forEach(el => io.observe(el));
    } else {
        revealEls.forEach(el => el.classList.add('in'));
        counters.forEach(countUp);
    }

    // 3D tilt on the hero debit card
    const card = document.querySelector('.tilt');
    if (card && !reduceMotion && window.matchMedia('(pointer: fine)').matches) {
        const area = card.parentElement;
        area.addEventListener('mousemove', (e) => {
            const r = area.getBoundingClientRect();
            const x = (e.clientX - r.left) / r.width - .5;
            const y = (e.clientY - r.top) / r.height - .5;
            card.style.transform = `rotateX(${10 - y * 14}deg) rotateY(${18 + x * 20}deg) rotateZ(-6deg)`;
        });
        area.addEventListener('mouseleave', () => { card.style.transform = ''; });
    }

    // SIP / FD calculator (illustration only)
    const calc = document.querySelector('[data-calc]');
    if (calc) {
        const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
        const input = (name) => calc.querySelector(`[data-in="${name}"]`);
        const out = (name, text) => { calc.querySelector(`[data-out="${name}"]`).textContent = text; };
        let mode = 'sip';
        function update() {
            const amount = Number(input('amount').value);
            const years = Number(input('years').value);
            const rate = Number(input('rate').value);
            let invested, total;
            if (mode === 'sip') {
                const r = rate / 12 / 100, n = years * 12;
                invested = amount * n;
                total = amount * ((Math.pow(1 + r, n) - 1) / r) * (1 + r);
            } else {
                invested = amount;
                total = amount * Math.pow(1 + rate / 400, 4 * years); // compounded quarterly, like most bank FDs
            }
            out('amount', inr.format(amount));
            out('years', years + (years === 1 ? ' yr' : ' yrs'));
            out('rate', rate + '%');
            out('invested', inr.format(invested));
            out('returns', inr.format(total - invested));
            out('total', inr.format(total));
            calc.querySelector('[data-bar]').style.width = (invested / total * 100).toFixed(1) + '%';
        }
        calc.querySelectorAll('[data-mode]').forEach((tab) => tab.addEventListener('click', () => {
            mode = tab.dataset.mode;
            calc.querySelectorAll('[data-mode]').forEach((t) => {
                t.classList.toggle('on', t === tab);
                t.setAttribute('aria-selected', String(t === tab));
            });
            const amount = input('amount');
            if (mode === 'fd') {
                calc.querySelector('[data-label-amount]').textContent = 'Deposit amount';
                amount.min = 5000; amount.max = 1000000; amount.step = 5000; amount.value = 100000;
                input('rate').value = 7;
            } else {
                calc.querySelector('[data-label-amount]').textContent = 'Monthly investment';
                amount.min = 500; amount.max = 100000; amount.step = 500; amount.value = 5000;
                input('rate').value = 12;
            }
            update();
        }));
        calc.querySelectorAll('input[type=range]').forEach((r) => r.addEventListener('input', update));
        update();
    }

    // KYC uploads: show the chosen file name, an image preview, and highlight when dragging a file over
    document.querySelectorAll('[data-upload]').forEach((box) => {
        const zone = box.querySelector('.dropzone');
        const file = box.querySelector('input[type=file]');
        const preview = box.querySelector('.dz-preview');
        const text = box.querySelector('.dz-text');
        ['dragenter', 'dragover'].forEach((ev) => zone.addEventListener(ev, () => zone.classList.add('drag')));
        ['dragleave', 'drop'].forEach((ev) => zone.addEventListener(ev, () => zone.classList.remove('drag')));
        file.addEventListener('change', () => {
            const f = file.files[0];
            zone.classList.remove('invalid');
            if (!f) return;
            const kb = Math.ceil(f.size / 1024);
            text.innerHTML = '';
            const strong = document.createElement('strong');
            strong.textContent = f.name;
            text.append(strong, ` (${kb} KB)`);
            if (f.size > 2 * 1024 * 1024) {
                zone.classList.add('invalid');
                text.append(' · too large, max 2 MB');
            }
            if (f.type.startsWith('image/')) {
                preview.src = URL.createObjectURL(f);
                preview.hidden = false;
            } else {
                preview.hidden = true;
            }
        });
    });
})();
