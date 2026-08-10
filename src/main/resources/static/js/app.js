document.addEventListener('DOMContentLoaded', function () {
    var form = document.getElementById('manual-form');
    var submitBtn = document.getElementById('submit-btn');
    var errorBox = document.getElementById('error-box');
    var loadingBox = document.getElementById('loading-box');
    var loadingTimer = null;

    // ---- AI provider badge ----
    fetch('/api/config')
        .then(function (res) { return res.json(); })
        .then(function (cfg) {
            var names = { openai: 'OpenAI', gemini: 'Gemini', ollama: 'Ollama (local)' };
            document.getElementById('ai-provider-name').textContent = names[cfg.aiProvider] || cfg.aiProvider;
        })
        .catch(function () { document.getElementById('ai-provider-name').textContent = 'AI'; });

    // ---- Dark mode ----
    var darkToggle = document.getElementById('dark-toggle');
    if (localStorage.getItem('cg-dark') === '1') document.body.classList.add('dark');
    darkToggle.addEventListener('click', function () {
        document.body.classList.toggle('dark');
        localStorage.setItem('cg-dark', document.body.classList.contains('dark') ? '1' : '0');
    });

    loadReviews();

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        errorBox.classList.add('hidden');
        document.getElementById('latest-review').classList.add('hidden');

        var repo = document.getElementById('repo-input').value.trim();
        var prNumber = parseInt(document.getElementById('pr-input').value, 10);
        var postComment = document.getElementById('post-comment-input').checked;

        if (!repo.includes('/')) {
            showError('Repo should be in "owner/repo" format.');
            return;
        }

        submitBtn.disabled = true;
        startLoadingChecklist();

        fetch('/api/reviews/manual', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ repoFullName: repo, prNumber: prNumber, postComment: postComment })
        })
            .then(function (res) { return res.json().then(function (data) { return { ok: res.ok, data: data }; }); })
            .then(function (result) {
                submitBtn.disabled = false;
                stopLoadingChecklist();
                if (!result.ok) { showError(result.data.message || 'Review failed. Please try again.'); return; }
                renderLatestReview(result.data);
                loadReviews();
            })
            .catch(function () {
                submitBtn.disabled = false;
                stopLoadingChecklist();
                showError('Could not reach the server. Please try again.');
            });
    });

    function startLoadingChecklist() {
        var steps = document.querySelectorAll('.loading-step');
        steps.forEach(function (s) { s.classList.remove('done', 'active'); s.querySelector('.step-icon').textContent = '\u25CB'; });
        loadingBox.classList.remove('hidden');
        var idx = 0;
        steps[0].classList.add('active');
        loadingTimer = setInterval(function () {
            if (idx < steps.length) {
                steps[idx].classList.remove('active');
                steps[idx].classList.add('done');
                steps[idx].querySelector('.step-icon').textContent = '\u2714';
            }
            idx++;
            if (idx < steps.length) steps[idx].classList.add('active');
        }, 2200);
    }

    function stopLoadingChecklist() {
        clearInterval(loadingTimer);
        document.querySelectorAll('.loading-step').forEach(function (s) {
            s.classList.remove('active'); s.classList.add('done');
            s.querySelector('.step-icon').textContent = '\u2714';
        });
        setTimeout(function () { loadingBox.classList.add('hidden'); }, 300);
    }

    function loadReviews() {
        fetch('/api/reviews')
            .then(function (res) { return res.json(); })
            .then(function (reviews) {
                document.getElementById('empty-box').classList.toggle('hidden', reviews.length > 0);
                document.getElementById('history-heading').classList.toggle('hidden', reviews.length === 0);
                renderReviews(reviews);
            })
            .catch(function () { showError('Could not load review history.'); });
    }

    // ---- Latest review: prominent detail view right after running ----
    function renderLatestReview(r) {
        var findings = safeFindings(r);
        var critical = findings.filter(function (f) { return f.severity === 'Critical' || f.severity === 'High'; }).length;
        var suggestions = findings.length - critical;

        var el = document.getElementById('latest-review');
        el.innerHTML =
            '<div class="report-card latest-card" data-review-id="latest">' +
                '<div class="latest-meta-row">' +
                    '<div class="meta-item"><span class="meta-label">Repository</span><span class="meta-value">' + escapeHtml(r.repoFullName) + '</span></div>' +
                    '<div class="meta-item"><span class="meta-label">PR</span><span class="meta-value">#' + r.prNumber + '</span></div>' +
                    '<div class="meta-item"><span class="meta-label">Author</span><span class="meta-value">' + escapeHtml(r.prAuthor) + '</span></div>' +
                    '<div class="meta-item"><span class="meta-label">Files Changed</span><span class="meta-value">' + r.filesChanged + '</span></div>' +
                    '<div class="meta-item"><span class="meta-label">Lines</span><span class="meta-value"><span class="lines-add">+' + r.totalAdditions + '</span> / <span class="lines-del">-' + r.totalDeletions + '</span></span></div>' +
                '</div>' +
                '<a href="' + escapeHtml(r.prUrl) + '" target="_blank" class="latest-pr-link">' + escapeHtml(r.prTitle) + ' &rarr;</a>' +

                '<div class="score-summary-grid">' +
                    '<div class="score-tile"><span class="score-tile-value">' + r.overallScore + '/10</span><span class="score-tile-label">Overall Score</span></div>' +
                    '<div class="score-tile"><span class="score-tile-value risk-text-' + riskClass(r.riskLevel) + '">' + escapeHtml(r.riskLevel) + '</span><span class="score-tile-label">Risk</span></div>' +
                    '<div class="score-tile"><span class="score-tile-value">' + r.filesChanged + '</span><span class="score-tile-label">Files Reviewed</span></div>' +
                    '<div class="score-tile"><span class="score-tile-value critical-text">' + critical + '</span><span class="score-tile-label">Critical Issues</span></div>' +
                    '<div class="score-tile"><span class="score-tile-value">' + suggestions + '</span><span class="score-tile-label">Suggestions</span></div>' +
                '</div>' +

                '<p class="review-summary">' + escapeHtml(r.overallSummary) + '</p>' +
                '<div class="report-actions">' +
                    (r.commentPosted ? '<span class="posted-tag">&#10003; Posted to GitHub</span>' : '') +
                    '<a href="/api/reviews/' + r.id + '/markdown" class="ghost-btn">Download Markdown</a>' +
                    '<button type="button" class="ghost-btn print-pdf-btn" data-target="latest">Download PDF</button>' +
                '</div>' +
                '<div class="findings-list" id="latest-findings"></div>' +
            '</div>';

        var container = document.getElementById('latest-findings');
        findings.forEach(function (f) { container.appendChild(buildFindingCard(f)); });

        bindPrintButtons(el);
        el.classList.remove('hidden');
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    // ---- History list ----
    var allReviewsCache = [];

    function renderReviews(reviews) {
        allReviewsCache = reviews;
        populateRepoFilter(reviews);
        renderTrendsChart(reviews);
        applyFilterAndRender();
    }

    function populateRepoFilter(reviews) {
        var select = document.getElementById('repo-filter');
        var current = select.value;
        var repos = Array.from(new Set(reviews.map(function (r) { return r.repoFullName; }))).sort();
        document.getElementById('history-toolbar').classList.toggle('hidden', repos.length === 0);

        select.innerHTML = '<option value="">All repositories</option>';
        repos.forEach(function (repo) {
            var opt = document.createElement('option');
            opt.value = repo;
            opt.textContent = repo;
            select.appendChild(opt);
        });
        select.value = repos.includes(current) ? current : '';
    }

    document.getElementById('repo-filter').addEventListener('change', applyFilterAndRender);

    function applyFilterAndRender() {
        var filter = document.getElementById('repo-filter').value;
        var filtered = filter ? allReviewsCache.filter(function (r) { return r.repoFullName === filter; }) : allReviewsCache;
        renderReviewCards(filtered);
    }

    function renderReviewCards(reviews) {
        var list = document.getElementById('reviews-list');
        list.innerHTML = '';
        reviews.forEach(function (r) {
            var findings = safeFindings(r);

            var card = document.createElement('div');
            card.className = 'report-card review-card';
            card.setAttribute('data-review-id', r.id);
            card.innerHTML =
                '<div class="review-head">' +
                    '<div>' +
                        '<span class="risk-badge risk-' + riskClass(r.riskLevel) + '">' + escapeHtml(r.riskLevel) + '</span>' +
                        '<span class="score-chip">' + r.overallScore + '/10</span>' +
                        '<a href="' + escapeHtml(r.prUrl) + '" target="_blank" class="review-title">' + escapeHtml(r.repoFullName) + ' #' + r.prNumber + ' &mdash; ' + escapeHtml(r.prTitle) + '</a>' +
                    '</div>' +
                    '<span class="review-meta">' + r.filesChanged + ' files &middot; <span class="lines-add">+' + r.totalAdditions + '</span>/<span class="lines-del">-' + r.totalDeletions + '</span> &middot; ' + formatDate(r.createdAt) +
                        (r.commentPosted ? ' &middot; <span class="posted-tag">Posted</span>' : '') + '</span>' +
                '</div>' +
                '<p class="review-summary">' + escapeHtml(r.overallSummary) + '</p>' +
                '<div class="report-actions">' +
                    '<button type="button" class="ghost-btn toggle-findings-btn">Show findings (' + findings.length + ')</button>' +
                    '<a href="/api/reviews/' + r.id + '/markdown" class="ghost-btn">Download Markdown</a>' +
                    '<button type="button" class="ghost-btn print-pdf-btn" data-target="' + r.id + '">Download PDF</button>' +
                '</div>' +
                '<div class="findings-list hidden"></div>';

            var findingsContainer = card.querySelector('.findings-list');
            findings.forEach(function (f) { findingsContainer.appendChild(buildFindingCard(f)); });

            card.querySelector('.toggle-findings-btn').addEventListener('click', function () {
                findingsContainer.classList.toggle('hidden');
            });

            list.appendChild(card);
        });
        bindPrintButtons(list);
    }

    // ---- PDF export via scoped browser print ----
    function bindPrintButtons(scope) {
        scope.querySelectorAll('.print-pdf-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var targetId = btn.getAttribute('data-target');
                var target = document.querySelector('[data-review-id="' + targetId + '"]');
                if (!target) return;

                var allSections = document.querySelectorAll('.report-card, .hero, .topbar, #history-heading');
                allSections.forEach(function (el) { if (el !== target) el.classList.add('print-hide'); });

                window.print();

                function restore() {
                    allSections.forEach(function (el) { el.classList.remove('print-hide'); });
                    window.removeEventListener('afterprint', restore);
                }
                window.addEventListener('afterprint', restore);
            });
        });
    }

    // ---- Trends chart ----
    function renderTrendsChart(reviews) {
        var card = document.getElementById('trends-card');
        if (reviews.length < 2) { card.classList.add('hidden'); return; }
        card.classList.remove('hidden');

        var sorted = reviews.slice().sort(function (a, b) { return new Date(a.createdAt) - new Date(b.createdAt); });
        var w = 700, h = 200, pad = 34;

        var scorePoints = sorted.map(function (r, i) {
            var x = pad + (i / (sorted.length - 1)) * (w - pad * 2);
            var y = h - pad - (r.overallScore / 10) * (h - pad * 2);
            return [x, y];
        });
        var maxFindings = Math.max.apply(null, sorted.map(function (r) { return r.findingCount; }).concat([1]));
        var findingPoints = sorted.map(function (r, i) {
            var x = pad + (i / (sorted.length - 1)) * (w - pad * 2);
            var y = h - pad - (r.findingCount / maxFindings) * (h - pad * 2);
            return [x, y];
        });

        function pathFor(points) {
            return points.map(function (p, i) { return (i === 0 ? 'M' : 'L') + p[0] + ',' + p[1]; }).join(' ');
        }

        var gridLines = [0, 0.5, 1].map(function (frac) {
            var y = h - pad - frac * (h - pad * 2);
            return '<line x1="' + pad + '" y1="' + y + '" x2="' + (w - pad) + '" y2="' + y + '" class="trend-grid"/>';
        }).join('');

        document.getElementById('trends-chart-container').innerHTML =
            '<div class="trend-legend"><span class="legend-score">&#9679; Quality Score</span><span class="legend-issues">&#9679; Issues Found</span></div>' +
            '<svg viewBox="0 0 ' + w + ' ' + h + '" class="trend-svg">' + gridLines +
            '<path d="' + pathFor(scorePoints) + '" class="trend-line trend-line-score"/>' +
            '<path d="' + pathFor(findingPoints) + '" class="trend-line trend-line-issues"/>' +
            '</svg>';
    }

    // ---- Shared premium finding card ----
    function buildFindingCard(f) {
        var block = document.createElement('div');
        block.className = 'finding-block';
        block.innerHTML =
            '<div class="finding-top">' +
                '<span class="severity-badge severity-' + riskClass(f.severity) + '">' + severityEmoji(f.severity) + ' ' + escapeHtml(f.severity) + '</span>' +
                '<code class="finding-file">' + escapeHtml(f.file) + '</code>' +
            '</div>' +
            (f.title ? '<p class="finding-title">' + escapeHtml(f.title) + '</p>' : '') +
            (f.lineHint ? '<span class="finding-hint">' + escapeHtml(f.lineHint) + '</span>' : '') +
            '<p class="finding-comment">' + escapeHtml(f.comment) + '</p>' +
            (f.codeSnippet ? '<pre class="finding-code">' + escapeHtml(f.codeSnippet) + '</pre>' : '') +
            (f.suggestion ? '<p class="finding-suggestion"><strong>Suggested fix:</strong> ' + escapeHtml(f.suggestion) + '</p>' : '');
        return block;
    }

    function severityEmoji(s) {
        var map = { Critical: '\uD83D\uDD34', High: '\uD83D\uDFE0', Medium: '\uD83D\uDFE1', Low: '\uD83D\uDFE2', Info: '\u26AA' };
        return map[s] || '\u26AA';
    }

    function safeFindings(r) {
        try { return JSON.parse(r.findingsJson).findings || []; } catch (e) { return []; }
    }

    function riskClass(level) {
        var l = (level || '').toLowerCase();
        if (l === 'critical') return 'critical';
        if (l === 'high') return 'high';
        if (l === 'medium') return 'medium';
        if (l === 'low') return 'low';
        return 'info';
    }

    function formatDate(iso) {
        var d = new Date(iso);
        return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }) + ' ' +
            d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
    }

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text == null ? '' : text;
        return div.innerHTML;
    }

    function showError(message) {
        errorBox.textContent = message;
        errorBox.classList.remove('hidden');
    }
});