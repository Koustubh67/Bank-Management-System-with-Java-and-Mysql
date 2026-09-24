// JavaBank ATM: keypad, side keys, card insert/eject, processing screen, cash dispensing and receipt printing.
// Everything here is presentation only; the server has already done (or will do) the real transaction.
(function () {
    document.documentElement.classList.add('js');
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const wait = (ms) => new Promise((resolve) => setTimeout(resolve, reduceMotion ? 0 : ms));
    const $ = (sel) => document.querySelector(sel);

    const atm = $('#atm');
    const screen = $('#screen');
    const card = $('#atmCard');
    const readerLight = $('#readerLight');
    if (!atm || !screen) return;

    // ---------- Sound (short beeps like a real keypad; can be switched off) ----------
    let soundOn = true;
    try { soundOn = localStorage.getItem('atmSound') !== 'off'; } catch (e) { /* storage blocked */ }
    let audio;
    function tone(freq, ms, type, volume) {
        if (!soundOn || reduceMotion) return;
        try {
            audio = audio || new (window.AudioContext || window.webkitAudioContext)();
            const osc = audio.createOscillator();
            const gain = audio.createGain();
            osc.type = type || 'square';
            osc.frequency.value = freq;
            gain.gain.value = volume || 0.03;
            osc.connect(gain).connect(audio.destination);
            osc.start();
            osc.stop(audio.currentTime + ms / 1000);
        } catch (e) { /* audio not available */ }
    }
    const beep = () => tone(1150, 70);
    const whirr = (ms) => tone(95, ms, 'sawtooth', 0.025);
    const soundBtn = $('#soundToggle');
    function renderSound() {
        if (!soundBtn) return;
        soundBtn.textContent = soundOn ? '🔊 Sound on' : '🔇 Sound off';
        soundBtn.setAttribute('aria-pressed', String(soundOn));
    }
    if (soundBtn) {
        soundBtn.addEventListener('click', () => {
            soundOn = !soundOn;
            try { localStorage.setItem('atmSound', soundOn ? 'on' : 'off'); } catch (e) { /* ignore */ }
            renderSound();
        });
        renderSound();
    }

    // ---------- Clock in the screen's status bar ----------
    const clock = $('#atmClock');
    function tick() {
        if (clock) clock.textContent = new Date().toLocaleString('en-IN', {
            day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit'
        });
    }
    tick();
    setInterval(tick, 1000);

    // ---------- Processing overlay ----------
    function processing(text) {
        $('#processingText').textContent = text;
        $('#processing').hidden = false;
    }

    // ---------- Keypad types into the input on screen ----------
    let target = null;
    screen.addEventListener('focusin', (e) => {
        if (e.target.matches('input')) target = e.target;
    });
    function currentInput() {
        if (target && document.contains(target)) return target;
        return screen.querySelector('input[type=text], input[type=password], input[type=number], input:not([type])');
    }
    document.querySelectorAll('.keypad [data-key]').forEach((btn) => {
        btn.addEventListener('click', () => {
            beep();
            const input = currentInput();
            const key = btn.dataset.key;
            if (key === 'enter') {
                const form = input ? input.form : screen.querySelector('form');
                if (form) form.requestSubmit();
                return;
            }
            if (!input) return;
            if (key === 'clear') input.value = input.value.slice(0, -1);
            else if (key === 'cancel') input.value = '';
            else if (input.maxLength < 0 || input.value.length < input.maxLength) input.value += key;
            input.focus();
        });
    });

    // ---------- Side keys press the menu option next to them ----------
    document.querySelectorAll('.side-keys [data-side]').forEach((btn) => {
        const options = screen.querySelectorAll('.menu-opt.' + btn.dataset.side);
        const option = options[Number(btn.dataset.index)];
        btn.disabled = !option;
        btn.addEventListener('click', () => {
            if (!option) return;
            beep();
            option.classList.add('flash');
            setTimeout(() => option.click(), reduceMotion ? 0 : 150);
        });
    });

    // Submits a form after an animation. The flag lets the second submit event through.
    function submitAfter(form, submitter, ms) {
        wait(ms).then(() => {
            form.dataset.go = '1';
            form.requestSubmit(submitter || undefined);
        });
    }

    // ---------- Card insert (login screen) ----------
    const loginForm = screen.querySelector('form[data-insert-card]');
    if (loginForm) {
        atm.classList.add('card-out');
        card.classList.add('wiggle');
        readerLight.classList.add('on');
        loginForm.addEventListener('submit', (e) => {
            if (loginForm.dataset.go) return;
            e.preventDefault();
            card.classList.remove('wiggle');
            atm.classList.remove('card-out');
            readerLight.classList.remove('on');
            whirr(900);
            processing('Reading your card…');
            submitAfter(loginForm, e.submitter, 1300);
        });
    }

    // ---------- "Processing" before money operations ----------
    screen.querySelectorAll('form[data-processing]').forEach((form) => {
        form.addEventListener('submit', (e) => {
            if (form.dataset.go) return;
            e.preventDefault();
            processing('Processing your transaction…');
            submitAfter(form, e.submitter, 1100);
        });
    });

    // ---------- Card eject on exit ----------
    screen.querySelectorAll('form[data-eject]').forEach((form) => {
        form.addEventListener('submit', (e) => {
            if (form.dataset.go) return;
            e.preventDefault();
            processing('Please take your card');
            whirr(700);
            atm.classList.add('card-out');
            readerLight.classList.add('on');
            submitAfter(form, e.submitter, 1800);
        });
    });

    // ---------- Receipt screen: dispense or accept cash, then print ----------
    const stage = screen.querySelector('[data-atm-stage]');
    if (!stage) return;
    const kind = stage.dataset.kind;
    const notes = (stage.dataset.notes || '').split(',').filter(Boolean);
    const tray = $('#cashTray');
    const cashLight = $('#cashLight');
    const paper = $('#paper');
    const show = (name) => stage.querySelectorAll('[data-step]').forEach((s) => { s.hidden = s.dataset.step !== name; });

    function addNotes(extraClass) {
        tray.innerHTML = '';
        tray.classList.remove('taken');
        tray.classList.add('open');
        notes.slice(0, 14).forEach((value, i) => {
            const note = document.createElement('span');
            note.className = 'note n' + value + (extraClass ? ' ' + extraClass : '');
            note.textContent = '₹' + value;
            note.style.setProperty('--y', (8 + i * 3) + 'px');
            note.style.setProperty('--r', ((i % 3) - 1) * 1.5 + 'deg');
            note.style.animationDelay = (reduceMotion ? 0 : i * 110) + 'ms';
            tray.appendChild(note);
        });
        return Math.min(notes.length, 14) * 110 + 600;
    }

    async function printReceipt() {
        const content = $('#receiptContent');
        if (!paper || !content) return;
        paper.innerHTML = '';
        paper.appendChild(content.cloneNode(true)).removeAttribute('id');
        whirr(2200);
        paper.classList.add('printing');
        await wait(2500);
        paper.classList.add('torn');
        paper.title = 'Click to take the receipt';
        paper.addEventListener('click', () => paper.classList.remove('printing'), { once: true });
    }

    async function run() {
        if (kind === 'CASH_OUT') {
            show('counting');
            whirr(1500);
            await wait(1700);
            cashLight.classList.add('on');
            await wait(addNotes(''));
            show('collect');
            const takeBtn = stage.querySelector('[data-take-cash]');
            takeBtn.focus();
            await new Promise((resolve) => takeBtn.addEventListener('click', resolve, { once: true }));
            beep();
            tray.classList.add('taken');
            cashLight.classList.remove('on');
            await wait(650);
            tray.innerHTML = '';
            tray.classList.remove('open');
            show('done');
            await printReceipt();
        } else if (kind === 'CASH_IN') {
            show('counting');
            cashLight.classList.add('on');
            const duration = addNotes('in');
            whirr(duration);
            await wait(duration + 300);
            tray.innerHTML = '';
            tray.classList.remove('open');
            cashLight.classList.remove('on');
            show('accepted');
            await wait(1300);
            show('done');
            await printReceipt();
        } else {
            show('done');
            await printReceipt();
        }
    }
    run();
})();
