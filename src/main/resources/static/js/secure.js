// Signed-in pages: if the browser restores this page from its back/forward cache (e.g. swiping back after logout),
// reload it so the server checks the login again instead of showing old account data.
window.addEventListener('pageshow', function (e) {
    if (e.persisted) window.location.reload();
});
