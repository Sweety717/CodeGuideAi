document.addEventListener('DOMContentLoaded', function () {
    var darkToggle = document.getElementById('dark-toggle');
    if (localStorage.getItem('cg-dark') === '1') document.body.classList.add('dark');
    darkToggle.addEventListener('click', function () {
        document.body.classList.toggle('dark');
        localStorage.setItem('cg-dark', document.body.classList.contains('dark') ? '1' : '0');
    });

    var providerNames = { openai: 'OpenAI', gemini: 'Gemini', ollama: 'Ollama (local)' };
    var select = document.getElementById('provider-select');
    var instructionsInput = document.getElementById('instructions-input');
    var tokenInput = document.getElementById('github-token-input');
    var secretInput = document.getElementById('webhook-secret-input');
    var reposInput = document.getElementById('allowed-repos-input');
    var errorBox = document.getElementById('error-box');
    var savedBox = document.getElementById('saved-box');

    loadSettings();

    function loadSettings() {
        fetch('/api/settings')
            .then(function (res) { return res.json(); })
            .then(function (settings) {
                select.innerHTML = '<option value="">Use default (from application.properties)</option>';
                settings.availableProviders.forEach(function (p) {
                    var opt = document.createElement('option');
                    opt.value = p;
                    opt.textContent = providerNames[p] || p;
                    select.appendChild(opt);
                });
                if (settings.isProviderOverridden) select.value = settings.activeProvider;

                instructionsInput.value = settings.customInstructions || '';
                reposInput.value = settings.allowedRepos || '';
                document.getElementById('token-mask').textContent = settings.githubTokenMasked || 'not configured';
            })
            .catch(function () { showError('Could not load settings.'); });
    }

    document.getElementById('settings-form').addEventListener('submit', function (e) {
        e.preventDefault();
        errorBox.classList.add('hidden');
        savedBox.classList.add('hidden');

        // Blank token/secret fields mean "don't change" - only include them in the
        // payload if the user actually typed something.
        var payload = {
            activeAiProviderOverride: select.value,
            customInstructions: instructionsInput.value.trim(),
            allowedRepos: reposInput.value.trim()
        };
        if (tokenInput.value.trim()) payload.githubTokenOverride = tokenInput.value.trim();
        if (secretInput.value.trim()) payload.webhookSecretOverride = secretInput.value.trim();

        fetch('/api/settings', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        })
            .then(function (res) { if (!res.ok) throw new Error(); return res.json(); })
            .then(function () {
                tokenInput.value = '';
                secretInput.value = '';
                loadSettings();
                savedBox.classList.remove('hidden');
                setTimeout(function () { savedBox.classList.add('hidden'); }, 2500);
            })
            .catch(function () { showError('Could not save settings. Please try again.'); });
    });

    function showError(message) {
        errorBox.textContent = message;
        errorBox.classList.remove('hidden');
    }
});