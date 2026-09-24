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

    // Dashboard balance is hidden until the customer taps "Show" (like banking apps, for privacy)
    document.querySelectorAll('[data-toggle-balance]').forEach((btn) => {
        const amount = btn.parentElement.querySelector('[data-balance]');
        const hidden = amount.textContent;
        btn.addEventListener('click', () => {
            const show = btn.getAttribute('aria-pressed') !== 'true';
            amount.textContent = show ? amount.dataset.value : hidden;
            btn.textContent = show ? 'Hide' : 'Show';
            btn.setAttribute('aria-pressed', String(show));
        });
    });

    // Invest page: live FD maturity and SIP projection (the server calculates the real values)
    const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
    const fdForm = document.querySelector('[data-fd]');
    if (fdForm) {
        const update = () => {
            const amount = Number(fdForm.querySelector('[data-fd-amount]').value) || 0;
            const t = fdForm.querySelector('input[name=tenure]:checked');
            const total = amount * Math.pow(1 + Number(t.dataset.rate) / 400, Number(t.dataset.months) / 3);
            fdForm.querySelector('[data-fd-out]').textContent = rupees.format(total);
            fdForm.querySelector('[data-fd-gain]').textContent = '+' + rupees.format(total - amount) + ' interest';
        };
        fdForm.addEventListener('input', update);
        update();
    }
    const sipForm = document.querySelector('[data-sip]');
    if (sipForm) {
        const update = () => {
            const monthly = Number(sipForm.querySelector('[data-sip-amount]').value) || 0;
            const r = Number(sipForm.querySelector('input[name=fund]:checked').dataset.return) / 1200, n = 120;
            sipForm.querySelector('[data-sip-out]').textContent = rupees.format(monthly * ((Math.pow(1 + r, n) - 1) / r) * (1 + r));
        };
        sipForm.addEventListener('input', update);
        update();
    }
    // Insurance page: show the premium for the chosen cover
    document.querySelectorAll('[data-plan]').forEach((form) => {
        const update = () => {
            const checked = form.querySelector('input[name=cover]:checked');
            if (checked) form.querySelector('[data-premium-out]').textContent = checked.dataset.premium;
        };
        form.addEventListener('change', update);
        update();
    });

    // ---------- Home v2 effects ----------
    // Scroll progress bar
    const progress = document.querySelector('.scroll-progress i');
    if (progress) {
        const onScroll = () => {
            const max = document.documentElement.scrollHeight - innerHeight;
            progress.style.width = (max > 0 ? scrollY / max * 100 : 0) + '%';
        };
        addEventListener('scroll', onScroll, { passive: true });
        onScroll();
    }

    // Rotating hero word: save. → pay. → grow. → protect.
    const words = document.querySelectorAll('.rot-word');
    if (words.length && !reduceMotion) {
        let i = 0;
        setInterval(() => {
            words[i].classList.remove('on');
            words[i].classList.add('out');
            const prev = words[i];
            setTimeout(() => prev.classList.remove('out'), 600);
            i = (i + 1) % words.length;
            words[i].classList.add('on');
        }, 2200);
    }

    // Hero orb drifts slightly with the mouse
    const orb = document.querySelector('.hx-orb');
    if (orb && !reduceMotion) {
        addEventListener('mousemove', (e) => {
            orb.style.transform = `translate(${(e.clientX / innerWidth - .5) * -40}px, ${(e.clientY / innerHeight - .5) * -40}px)`;
        }, { passive: true });
    }

    // Product showcase: tabs that advance on their own, pause on hover or focus
    const sc = document.querySelector('[data-showcase]');
    if (sc) {
        const tabs = [...sc.querySelectorAll('[role=tab]')].filter((t) => t.closest('.sc-tabs'));
        const TAB_MS = 6000;
        sc.style.setProperty('--tab-ms', TAB_MS + 'ms');
        let current = 0, timer = null, paused = false;
        function select(index, focus) {
            tabs.forEach((t, n) => {
                const on = n === index;
                t.classList.toggle('on', on);
                t.setAttribute('aria-selected', String(on));
                t.tabIndex = on ? 0 : -1;
                const panel = document.getElementById(t.getAttribute('aria-controls'));
                panel.hidden = !on;
                panel.classList.toggle('on', on);
            });
            // restart the progress bar animation
            const bar = tabs[index].querySelector('i');
            bar.style.animation = 'none'; void bar.offsetWidth; bar.style.animation = '';
            current = index;
            if (focus) tabs[index].focus();
            schedule();
        }
        function schedule() {
            clearTimeout(timer);
            if (!paused && !reduceMotion) timer = setTimeout(() => select((current + 1) % tabs.length), TAB_MS);
        }
        tabs.forEach((t, n) => {
            t.addEventListener('click', () => select(n));
            t.addEventListener('keydown', (e) => {
                if (e.key === 'ArrowRight') select((current + 1) % tabs.length, true);
                if (e.key === 'ArrowLeft') select((current + tabs.length - 1) % tabs.length, true);
            });
        });
        const pause = (p) => { paused = p; sc.classList.toggle('paused', p); if (!p) schedule(); else clearTimeout(timer); };
        sc.addEventListener('mouseenter', () => pause(true));
        sc.addEventListener('mouseleave', () => pause(false));
        sc.addEventListener('focusin', () => pause(true));
        sc.addEventListener('focusout', () => pause(false));
        // Nav links like "Invest" open the matching tab
        document.querySelectorAll('[data-tab-link]').forEach((a) => a.addEventListener('click', () => {
            const n = tabs.findIndex((t) => t.id === a.dataset.tabLink);
            if (n >= 0) select(n);
        }));
        if (reduceMotion) sc.querySelector('.sc-hint').hidden = true;
        schedule();
    }

    // Magnetic buttons and custom cursor (mouse only)
    if (!reduceMotion && matchMedia('(pointer: fine)').matches) {
        document.querySelectorAll('.magnetic').forEach((el) => {
            el.addEventListener('mousemove', (e) => {
                const r = el.getBoundingClientRect();
                el.style.transform = `translate(${(e.clientX - r.left - r.width / 2) * .25}px, ${(e.clientY - r.top - r.height / 2) * .35}px)`;
            });
            el.addEventListener('mouseleave', () => { el.style.transform = ''; });
        });
        const cursor = document.querySelector('.cursor');
        if (cursor && document.querySelector('.hx')) {
            let x = 0, y = 0, cx = 0, cy = 0;
            addEventListener('mousemove', (e) => { x = e.clientX; y = e.clientY; cursor.classList.add('show'); }, { passive: true });
            (function follow() {
                cx += (x - cx) * .2; cy += (y - cy) * .2;
                cursor.style.transform = `translate(${cx}px, ${cy}px)`;
                requestAnimationFrame(follow);
            })();
            document.querySelectorAll('a, button, summary, input, [role=tab]').forEach((el) => {
                el.addEventListener('mouseenter', () => cursor.classList.add('big'));
                el.addEventListener('mouseleave', () => cursor.classList.remove('big'));
            });
        }
    }

    // Fund page: SIP vs one-time changes the amount label and minimum; chips fill the amount
    document.querySelectorAll('[data-invest-box]').forEach((form) => {
        const amount = form.querySelector('input[name=amount]');
        const label = form.querySelector('[data-amount-label]');
        form.querySelectorAll('input[name=type]').forEach((r) => r.addEventListener('change', () => {
            const sip = form.querySelector('input[name=type]:checked').value === 'SIP';
            label.textContent = sip ? 'Monthly amount (₹)' : 'One-time amount (₹)';
            amount.min = sip ? 500 : 1000;
        }));
        form.querySelectorAll('[data-amt]').forEach((chip) => chip.addEventListener('click', () => {
            amount.value = chip.dataset.amt;
            amount.focus();
        }));
    });

    // Checkout: short "processing" overlay while the payment is sent
    document.querySelectorAll('[data-processing-pay]').forEach((form) => {
        form.addEventListener('submit', (e) => {
            const submitter = e.submitter;
            if (submitter && submitter.formAction && submitter.formAction.endsWith('/otp')) return;
            const overlay = document.querySelector('[data-pay-overlay]');
            if (overlay) overlay.hidden = false;
        });
    });
})();
