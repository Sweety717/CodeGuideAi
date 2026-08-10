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
                verdictBanner(r.mergeRecommendation) +
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
                    '<div class="score-tile"><span class="score-tile-value">' + r.confidence + '%</span><span class="score-tile-label">AI Confidence</span></div>' +
                    '<div class="score-tile"><span class="score-tile-value">' + formatDuration(r.reviewDurationMs) + '</span><span class="score-tile-label">Completed In</span></div>' +
                    '<div class="score-tile"><span class="score-tile-value critical-text">' + critical + '</span><span class="score-tile-label">Critical Issues</span></div>' +
                    '<div class="score-tile"><span class="score-tile-value">' + suggestions + '</span><span class="score-tile-label">Suggestions</span></div>' +
                '</div>' +

                '<p class="review-summary">' + escapeHtml(r.overallSummary) + '</p>' +
                '<div class="report-actions">' +
                    (r.commentPosted ? '<span class="posted-tag">&#10003; Posted to GitHub</span>' : '') +
                    '<a href="/api/reviews/' + r.id + '/markdown" class="ghost-btn">Download Markdown</a>' +
                    '<button type="button" class="ghost-btn print-pdf-btn" data-target="latest">Download PDF</button>' +
                '</div>' +
                buildFilterBar('latest') +
                '<div class="findings-list" id="latest-findings"></div>' +
            '</div>';

        var container = document.getElementById('latest-findings');
        findings.forEach(function (f) { container.appendChild(buildFindingCard(f, r.repoFullName, r.headSha)); });

        bindPrintButtons(el);
        bindFilterBar(el, container);
        el.classList.remove('hidden');
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function verdictBanner(recommendation) {
        var cls = 'verdict-safe', icon = '\u2705';
        if (recommendation === 'Do Not Merge') { cls = 'verdict-danger'; icon = '\u274C'; }
        else if (recommendation === 'Merge After Fixes') { cls = 'verdict-warn'; icon = '\u26A0\uFE0F'; }
        return '<div class="verdict-banner ' + cls + '">' + icon + ' ' + escapeHtml((recommendation || 'Safe to Merge').toUpperCase()) + '</div>';
    }

    function formatDuration(ms) {
        if (!ms) return '\u2014';
        return (ms / 1000).toFixed(1) + 's';
    }

    // ---- Findings filter bar (severity + category combined, matches the ask) ----
    var FILTER_CHIPS = ['All', 'Critical', 'High', 'Medium', 'Low', 'Security', 'Performance', 'Best Practice'];

    function buildFilterBar(scopeId) {
        var chips = FILTER_CHIPS.map(function (label, i) {
            return '<button type="button" class="filter-chip' + (i === 0 ? ' active' : '') + '" data-filter="' + label + '" data-scope="' + scopeId + '">' + label + '</button>';
        }).join('');
        return '<div class="filter-bar">' + chips + '</div>';
    }

    function bindFilterBar(scopeEl, findingsContainer) {
        var chips = scopeEl.querySelectorAll('.filter-chip');
        chips.forEach(function (chip) {
            chip.addEventListener('click', function () {
                chips.forEach(function (c) { c.classList.remove('active'); });
                chip.classList.add('active');
                var filter = chip.getAttribute('data-filter');
                findingsContainer.querySelectorAll('.finding-block').forEach(function (block) {
                    var sev = block.getAttribute('data-severity');
                    var cat = block.getAttribute('data-category');
                    var match = filter === 'All' || sev === filter || cat === filter;
                    block.classList.toggle('hidden', !match);
                });
            });
        });
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
                        verdictChip(r.mergeRecommendation) +
                        '<span class="score-chip">' + r.overallScore + '/10</span>' +
                        '<a href="' + escapeHtml(r.prUrl) + '" target="_blank" class="review-title">' + escapeHtml(r.repoFullName) + ' #' + r.prNumber + ' &mdash; ' + escapeHtml(r.prTitle) + '</a>' +
                    '</div>' +
                    '<span class="review-meta">' + r.filesChanged + ' files &middot; <span class="lines-add">+' + r.totalAdditions + '</span>/<span class="lines-del">-' + r.totalDeletions + '</span> &middot; ' + formatDuration(r.reviewDurationMs) + ' &middot; ' + formatDate(r.createdAt) +
                        (r.commentPosted ? ' &middot; <span class="posted-tag">Posted</span>' : '') + '</span>' +
                '</div>' +
                '<p class="review-summary">' + escapeHtml(r.overallSummary) + '</p>' +
                '<div class="report-actions">' +
                    '<button type="button" class="ghost-btn toggle-findings-btn">Show findings (' + findings.length + ')</button>' +
                    '<a href="/api/reviews/' + r.id + '/markdown" class="ghost-btn">Download Markdown</a>' +
                    '<button type="button" class="ghost-btn print-pdf-btn" data-target="' + r.id + '">Download PDF</button>' +
                '</div>' +
                buildFilterBar(String(r.id)) +
                '<div class="findings-list hidden"></div>';

            var findingsContainer = card.querySelector('.findings-list');
            findings.forEach(function (f) { findingsContainer.appendChild(buildFindingCard(f, r.repoFullName, r.headSha)); });
            bindFilterBar(card, findingsContainer);

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
    function buildFindingCard(f, repoFullName, headSha) {
        var block = document.createElement('div');
        block.className = 'finding-block';
        block.setAttribute('data-severity', f.severity);
        block.setAttribute('data-category', f.category);

        var fileDisplay = escapeHtml(f.file) + (f.lineNumber > 0 ? ':' + f.lineNumber : '');
        var fileHtml;
        if (f.lineNumber > 0 && repoFullName && headSha) {
            var link = 'https://github.com/' + repoFullName + '/blob/' + headSha + '/' + f.file + '#L' + f.lineNumber;
            fileHtml = '<a href="' + link + '" target="_blank" class="finding-file">' + fileDisplay + ' &#8599;</a>';
        } else {
            fileHtml = '<code class="finding-file finding-file-plain">' + fileDisplay + '</code>';
        }

        block.innerHTML =
            '<div class="finding-top">' +
                '<span class="severity-badge severity-' + riskClass(f.severity) + '">' + severityEmoji(f.severity) + ' ' + escapeHtml(f.severity) + '</span>' +
                '<span class="category-badge">' + categoryIcon(f.category) + ' ' + escapeHtml(f.category) + '</span>' +
            '</div>' +
            fileHtml +
            (f.title ? '<p class="finding-title">' + escapeHtml(f.title) + '</p>' : '') +
            (!f.lineNumber && f.lineHint ? '<span class="finding-hint">' + escapeHtml(f.lineHint) + '</span>' : '') +
            '<p class="finding-comment">' + escapeHtml(f.comment) + '</p>' +
            (f.codeSnippet ? codeBlockHtml(f.codeSnippet) : '') +
            (f.suggestion ? suggestionHtml(f.suggestion) : '');

        bindCopyButtons(block);
        return block;
    }

    function codeBlockHtml(snippet) {
        return '<div class="code-wrap"><button type="button" class="copy-btn" data-copy-text="' + escapeAttr(snippet) + '">&#128203; Copy</button>' +
            '<pre class="finding-code">' + escapeHtml(snippet) + '</pre></div>';
    }

    function suggestionHtml(suggestion) {
        return '<div class="suggestion-wrap"><button type="button" class="copy-btn copy-btn-suggestion" data-copy-text="' + escapeAttr(suggestion) + '">&#128203; Copy</button>' +
            '<p class="finding-suggestion"><strong>Suggested fix:</strong> ' + escapeHtml(suggestion) + '</p></div>';
    }

    function bindCopyButtons(scope) {
        scope.querySelectorAll('.copy-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var text = btn.getAttribute('data-copy-text');
                navigator.clipboard.writeText(text).then(function () {
                    var original = btn.textContent;
                    btn.textContent = '\u2714 Copied';
                    setTimeout(function () { btn.textContent = original; }, 1300);
                });
            });
        });
    }

    function severityEmoji(s) {
        var map = { Critical: '\uD83D\uDD34', High: '\uD83D\uDFE0', Medium: '\uD83D\uDFE1', Low: '\uD83D\uDFE2', Info: '\u26AA' };
        return map[s] || '\u26AA';
    }

    function categoryIcon(c) {
        var map = { Bug: '\uD83D\uDC1E', Security: '\uD83D\uDEE1\uFE0F', Performance: '\u26A1', Style: '\uD83C\uDFA8', 'Best Practice': '\uD83E\uDDF9' };
        return map[c] || '\u2022';
    }

    function escapeAttr(text) {
        return (text || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
    }

    function safeFindings(r) {
        try { return JSON.parse(r.findingsJson).findings || []; } catch (e) { return []; }
    }

    function verdictChip(recommendation) {
        var cls = 'verdict-safe', icon = '\u2705';
        if (recommendation === 'Do Not Merge') { cls = 'verdict-danger'; icon = '\u274C'; }
        else if (recommendation === 'Merge After Fixes') { cls = 'verdict-warn'; icon = '\u26A0\uFE0F'; }
        return '<span class="verdict-chip ' + cls + '">' + icon + ' ' + escapeHtml(recommendation || 'Safe to Merge') + '</span>';
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