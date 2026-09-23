// Makes the on-screen ATM keypad type into the input on the screen.
(function () {
    const screen = document.querySelector('.screen');
    if (!screen) return;
    let target = null;

    screen.addEventListener('focusin', function (e) {
        if (e.target.matches('input[type=text], input[type=password], input[type=number], input:not([type])')) {
            target = e.target;
        }
    });

    function currentInput() {
        if (target && document.contains(target)) return target;
        return screen.querySelector('input[type=text], input[type=password], input[type=number], input:not([type])');
    }

    document.querySelectorAll('.keypad [data-key]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            const input = currentInput();
            const key = btn.dataset.key;
            if (key === 'enter') {
                const form = input ? input.form : screen.querySelector('form');
                if (form) form.requestSubmit();
                return;
            }
            if (!input) return;
            if (key === 'clear') {
                input.value = input.value.slice(0, -1);
            } else if (key === 'cancel') {
                input.value = '';
            } else if (input.maxLength < 0 || input.value.length < input.maxLength) {
                input.value += key;
            }
            input.focus();
        });
    });
})();
