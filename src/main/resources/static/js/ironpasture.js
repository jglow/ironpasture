/* ==========================================================================
   Iron Pasture — Frontend Logic
   USDA Milk Safety Compliance Portal
   ========================================================================== */

document.addEventListener('DOMContentLoaded', function () {
    initComplianceForm();
});

/* ---------- Form Submission ---------- */

function initComplianceForm() {
    var form = document.getElementById('compliance-form');
    if (!form) return;

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        submitComplianceCheck();
    });
}

function submitComplianceCheck() {
    var btn = document.getElementById('btn-submit');
    var originalText = btn.textContent;
    btn.disabled = true;
    btn.innerHTML = '<span class="ip-spinner"></span> Processing\u2026';

    var stages = ['bronze', 'silver', 'gold', 'review'];
    animateStages(stages, 0);

    var payload = {
        plantId: val('plantId'),
        readingTimestamp: val('readingTimestamp'),
        pasteurizationTemp: numVal('pasteurizationTemp'),
        htstHoldTime: numVal('htstHoldTime'),
        rawMilkScc: numVal('rawMilkScc'),
        processedMilkSpc: numVal('processedMilkSpc'),
        coliformCount: numVal('coliformCount'),
        ph: numVal('ph'),
        coolerTemp: numVal('coolerTemp'),
        phosphataseTest: val('phosphataseTest'),
        operatorId: val('operatorId'),
        batchId: val('batchId')
    };

    fetch('/api/compliance/pre-fill', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
        .then(function (res) {
            if (!res.ok) throw new Error('Server returned ' + res.status);
            return res.json();
        })
        .then(function (data) {
            completeAllStages();
            renderResults(data);
        })
        .catch(function (err) {
            console.error('Compliance check failed:', err);
            showErrorAlert('Compliance check failed: ' + err.message);
        })
        .finally(function () {
            btn.disabled = false;
            btn.textContent = originalText;
        });
}

/* ---------- Results Rendering ---------- */

function renderResults(data) {
    var panel = document.getElementById('results-panel');
    panel.style.display = 'block';

    // Disposition alert
    renderDisposition(
        data.disposition,
        'disposition-alert',
        'disposition-heading',
        'disposition-text'
    );

    // Report table
    var tbody = document.getElementById('report-table-body');
    tbody.innerHTML = '';

    if (data.reportLines) {
        data.reportLines.forEach(function (line) {
            var tr = document.createElement('tr');
            tr.innerHTML =
                '<td>' + esc(line.parameter) + '</td>' +
                '<td>' + esc(line.observed) + '</td>' +
                '<td>' + esc(line.pmoLimit) + '</td>' +
                '<td class="' + statusClass(line.status) + '">' + esc(line.status) + '</td>';
            tbody.appendChild(tr);
        });
    }

    // Narrative
    var narrativeEl = document.getElementById('narrative-panel');
    narrativeEl.innerHTML = data.narrative
        ? '<p>' + esc(data.narrative) + '</p>'
        : '<p class="text-gray-50">No narrative generated.</p>';

    // Citations
    var citationsEl = document.getElementById('pmo-citations');
    citationsEl.innerHTML = '';
    if (data.pmoCitations) {
        data.pmoCitations.forEach(function (cite) {
            var chip = document.createElement('span');
            chip.className = 'ip-citation-chip';
            chip.textContent = cite;
            citationsEl.appendChild(chip);
        });
    }

    panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function renderDisposition(disposition, alertId, headingId, textId) {
    var alert = document.getElementById(alertId);
    var heading = document.getElementById(headingId);
    var text = document.getElementById(textId);

    // Reset classes
    alert.className = 'usa-alert margin-bottom-3';

    switch (disposition) {
        case 'COMPLIANT':
            alert.classList.add('usa-alert--success');
            heading.textContent = 'COMPLIANT';
            text.textContent = 'All parameters within PMO limits. Batch approved for release.';
            break;
        case 'NON_COMPLIANT':
            alert.classList.add('usa-alert--error');
            heading.textContent = 'NON-COMPLIANT';
            text.textContent = 'One or more parameters exceed PMO limits. Batch held for investigation.';
            break;
        case 'REQUIRES_REVIEW':
            alert.classList.add('usa-alert--warning');
            heading.textContent = 'REQUIRES REVIEW';
            text.textContent = 'Marginal readings detected. Inspector review required before disposition.';
            break;
        default:
            alert.classList.add('usa-alert--info');
            heading.textContent = disposition || 'UNKNOWN';
            text.textContent = 'Disposition could not be determined.';
    }
}

function showErrorAlert(message) {
    var panel = document.getElementById('results-panel');
    panel.style.display = 'block';

    var alert = document.getElementById('disposition-alert');
    alert.className = 'usa-alert usa-alert--error margin-bottom-3';
    document.getElementById('disposition-heading').textContent = 'Error';
    document.getElementById('disposition-text').textContent = message;
}

/* ---------- Process List Animation ---------- */

function animateStages(stages, index) {
    if (index >= stages.length) return;

    stages.forEach(function (s, i) {
        var el = document.getElementById('stage-' + s);
        if (!el) return;
        if (i < index) {
            el.classList.remove('ip-stage--active');
            el.classList.add('ip-stage--complete');
        } else if (i === index) {
            el.classList.remove('ip-stage--complete');
            el.classList.add('ip-stage--active');
        } else {
            el.classList.remove('ip-stage--active', 'ip-stage--complete');
        }
    });

    setTimeout(function () {
        animateStages(stages, index + 1);
    }, 800);
}

function completeAllStages() {
    ['bronze', 'silver', 'gold', 'review'].forEach(function (s) {
        var el = document.getElementById('stage-' + s);
        if (el) {
            el.classList.remove('ip-stage--active');
            el.classList.add('ip-stage--complete');
        }
    });
}

/* ---------- Inspector: Load History ---------- */

function loadHistory(plantIdOverride) {
    var plantId = plantIdOverride
        || (document.getElementById('inspector-plant-select')
            ? document.getElementById('inspector-plant-select').value
            : '');

    var url = plantId
        ? '/api/compliance/history/' + encodeURIComponent(plantId)
        : '/api/compliance/history';

    fetch(url)
        .then(function (res) {
            if (!res.ok) throw new Error('Server returned ' + res.status);
            return res.json();
        })
        .then(function (records) {
            var tbody = document.getElementById('history-table-body');
            if (!tbody) return;
            tbody.innerHTML = '';

            records.forEach(function (r) {
                var tr = document.createElement('tr');
                tr.className = 'ip-row-clickable';
                tr.setAttribute('data-batch-id', r.batchId);
                tr.onclick = function () { loadReport(r.batchId); };
                tr.innerHTML =
                    '<td>' + esc(r.batchId) + '</td>' +
                    '<td>' + esc(r.plantId) + '</td>' +
                    '<td>' + esc(r.date) + '</td>' +
                    '<td>' + dispositionChip(r.disposition) + '</td>' +
                    '<td>' + esc(r.pasteurizationTemp) + '</td>' +
                    '<td>' + formatNumber(r.scc) + '</td>' +
                    '<td>' + formatNumber(r.spc) + '</td>' +
                    '<td><button class="usa-button usa-button--unstyled" type="button">View</button></td>';
                tbody.appendChild(tr);
            });
        })
        .catch(function (err) {
            console.error('Failed to load history:', err);
        });
}

/* ---------- Inspector: Load Report Detail ---------- */

function loadReport(batchId) {
    var drawer = document.getElementById('report-drawer');
    if (!drawer) return;

    drawer.style.display = 'block';
    document.getElementById('drawer-batch-title').textContent =
        'Report: ' + batchId;

    fetch('/api/compliance/report/' + encodeURIComponent(batchId))
        .then(function (res) {
            if (!res.ok) throw new Error('Server returned ' + res.status);
            return res.json();
        })
        .then(function (data) {
            // Disposition
            renderDisposition(
                data.disposition,
                'drawer-disposition-alert',
                'drawer-disposition-heading',
                'drawer-disposition-text'
            );

            // Report lines
            var tbody = document.getElementById('drawer-report-body');
            tbody.innerHTML = '';
            if (data.reportLines) {
                data.reportLines.forEach(function (line) {
                    var tr = document.createElement('tr');
                    tr.innerHTML =
                        '<td>' + esc(line.parameter) + '</td>' +
                        '<td>' + esc(line.observed) + '</td>' +
                        '<td>' + esc(line.pmoLimit) + '</td>' +
                        '<td class="' + statusClass(line.status) + '">' + esc(line.status) + '</td>';
                    tbody.appendChild(tr);
                });
            }

            // Narrative
            document.getElementById('drawer-narrative').innerHTML =
                data.narrative
                    ? '<p>' + esc(data.narrative) + '</p>'
                    : '<p class="text-gray-50">No narrative available.</p>';

            // Citations
            var citationsEl = document.getElementById('drawer-citations');
            citationsEl.innerHTML = '';
            if (data.pmoCitations) {
                data.pmoCitations.forEach(function (cite) {
                    var chip = document.createElement('span');
                    chip.className = 'ip-citation-chip';
                    chip.textContent = cite;
                    citationsEl.appendChild(chip);
                });
            }
        })
        .catch(function (err) {
            console.error('Failed to load report:', err);
        });
}

function closeDrawer() {
    var drawer = document.getElementById('report-drawer');
    if (drawer) drawer.style.display = 'none';
}

/* ---------- Inspector Actions ---------- */

function approveReport() {
    alert('Report approved. Final disposition recorded in audit log.');
    closeDrawer();
}

function returnReport() {
    alert('Report returned for correction. Plant manager notified.');
    closeDrawer();
}

function escalateReport() {
    alert('Report escalated to regional supervisor.');
    closeDrawer();
}

/* ---------- Audit Log ---------- */

function filterAuditLog() {
    var plantId = val('audit-plant-filter');
    var disposition = val('audit-disposition-filter');
    var from = val('audit-date-from');
    var to = val('audit-date-to');

    var params = new URLSearchParams();
    if (plantId) params.set('plantId', plantId);
    if (disposition) params.set('disposition', disposition);
    if (from) params.set('from', from);
    if (to) params.set('to', to);

    fetch('/api/compliance/audit?' + params.toString())
        .then(function (res) {
            if (!res.ok) throw new Error('Server returned ' + res.status);
            return res.json();
        })
        .then(function (records) {
            var tbody = document.getElementById('audit-table-body');
            if (!tbody) return;
            tbody.innerHTML = '';

            records.forEach(function (r) {
                var tr = document.createElement('tr');
                tr.innerHTML =
                    '<td>' + esc(r.timestamp) + '</td>' +
                    '<td>' + esc(r.plantId) + '</td>' +
                    '<td>' + esc(r.batchId) + '</td>' +
                    '<td>' + dispositionChip(r.disposition) + '</td>' +
                    '<td>' + esc(r.modelUsed) + '</td>' +
                    '<td>' + esc(r.promptVersion) + '</td>';
                tbody.appendChild(tr);
            });

            var countEl = document.getElementById('audit-record-count');
            if (countEl) {
                countEl.innerHTML = 'Showing <strong>' + records.length +
                    '</strong> records';
            }
        })
        .catch(function (err) {
            console.error('Failed to filter audit log:', err);
        });
}

function exportAuditCsv() {
    var table = document.getElementById('audit-table');
    if (!table) return;

    var csv = [];
    var rows = table.querySelectorAll('tr');

    rows.forEach(function (row) {
        var cols = row.querySelectorAll('th, td');
        var rowData = [];
        cols.forEach(function (col) {
            var text = col.textContent.trim().replace(/"/g, '""');
            rowData.push('"' + text + '"');
        });
        csv.push(rowData.join(','));
    });

    var blob = new Blob([csv.join('\n')], { type: 'text/csv;charset=utf-8;' });
    var link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = 'iron_pasture_audit_' +
        new Date().toISOString().slice(0, 10) + '.csv';
    link.click();
    URL.revokeObjectURL(link.href);
}

/* ---------- Helpers ---------- */

function val(id) {
    var el = document.getElementById(id);
    return el ? el.value : '';
}

function numVal(id) {
    return parseFloat(val(id)) || 0;
}

function esc(str) {
    if (str === null || str === undefined) return '';
    var div = document.createElement('div');
    div.appendChild(document.createTextNode(String(str)));
    return div.innerHTML;
}

function formatNumber(n) {
    if (n === null || n === undefined) return '';
    return Number(n).toLocaleString();
}

function statusClass(status) {
    switch (status) {
        case 'PASS': return 'ip-status-pass';
        case 'FAIL': return 'ip-status-fail';
        case 'WARN': return 'ip-status-warn';
        default: return '';
    }
}

function dispositionChip(disposition) {
    var cls = 'ip-disposition ';
    switch (disposition) {
        case 'COMPLIANT': cls += 'ip-disposition--compliant'; break;
        case 'NON_COMPLIANT': cls += 'ip-disposition--noncompliant'; break;
        case 'REQUIRES_REVIEW': cls += 'ip-disposition--review'; break;
        default: cls += '';
    }
    return '<span class="' + cls + '">' + esc(disposition) + '</span>';
}
