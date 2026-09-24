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
            card.style.transform = `translate(-50%, -50%) rotateX(${8 - y * 16}deg) rotateY(${-14 + x * 24}deg)`;
        });
        area.addEventListener('mouseleave', () => { card.style.transform = ''; });
    }
})();
