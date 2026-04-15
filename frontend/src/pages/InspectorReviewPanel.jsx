import React, { useState, useEffect } from 'react';
import DispositionBadge from '../components/DispositionBadge';

/**
 * InspectorReviewPanel
 *
 * Allows certified inspectors to review AI-generated compliance reports,
 * view batch history, and approve/return/escalate dispositions.
 */
export default function InspectorReviewPanel() {
    const [plantId, setPlantId] = useState('');
    const [history, setHistory] = useState([]);
    const [selectedReport, setSelectedReport] = useState(null);

    async function loadHistory() {
        try {
            const url = plantId
                ? `/api/compliance/history/${encodeURIComponent(plantId)}`
                : '/api/compliance/history';

            const res = await fetch(url);
            if (!res.ok) throw new Error(`Server returned ${res.status}`);
            const data = await res.json();
            setHistory(data);
        } catch (err) {
            console.error('Failed to load history:', err);
        }
    }

    async function loadReport(batchId) {
        try {
            const res = await fetch(`/api/compliance/report/${encodeURIComponent(batchId)}`);
            if (!res.ok) throw new Error(`Server returned ${res.status}`);
            const data = await res.json();
            setSelectedReport(data);
        } catch (err) {
            console.error('Failed to load report:', err);
        }
    }

    return (
        <div>
            <h1 className="ip-page-title">Inspector Review Panel</h1>
            <p className="usa-intro">
                Review AI-generated compliance reports. Approve, return, or escalate.
            </p>

            {/* Plant selector */}
            <div className="grid-row grid-gap margin-bottom-4">
                <div className="grid-col-4">
                    <label className="usa-label" htmlFor="inspector-plant-select">
                        Select Plant
                    </label>
                    <select className="usa-select" id="inspector-plant-select"
                            value={plantId} onChange={(e) => setPlantId(e.target.value)}>
                        <option value="">-- All Plants --</option>
                        <option value="PMO-WI-4401">PMO-WI-4401</option>
                        <option value="PMO-CA-1102">PMO-CA-1102</option>
                        <option value="PMO-NY-3350">PMO-NY-3350</option>
                        <option value="PMO-TX-2210">PMO-TX-2210</option>
                    </select>
                </div>
                <div className="grid-col-4 flex-align-self-end">
                    <button className="usa-button usa-button--outline" onClick={loadHistory}>
                        Load Compliance History
                    </button>
                </div>
            </div>

            {/* History table */}
            <div className="usa-table-container--scrollable" tabIndex="0">
                <table className="usa-table usa-table--striped ip-history-table">
                    <thead>
                        <tr>
                            <th scope="col">Batch ID</th>
                            <th scope="col">Plant ID</th>
                            <th scope="col">Date</th>
                            <th scope="col">Disposition</th>
                            <th scope="col">Pasteurization</th>
                            <th scope="col">SCC</th>
                            <th scope="col">SPC</th>
                            <th scope="col">Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        {history.map((r) => (
                            <tr key={r.batchId} className="ip-row-clickable"
                                onClick={() => loadReport(r.batchId)}>
                                <td>{r.batchId}</td>
                                <td>{r.plantId}</td>
                                <td>{r.date}</td>
                                <td><DispositionBadge disposition={r.disposition} /></td>
                                <td>{r.pasteurizationTemp}</td>
                                <td>{Number(r.scc).toLocaleString()}</td>
                                <td>{Number(r.spc).toLocaleString()}</td>
                                <td>
                                    <button className="usa-button usa-button--unstyled">
                                        View
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {/* TODO: Report detail drawer/modal when selectedReport is set */}
            {/* TODO: Inspector action buttons (approve, return, escalate) */}
            {/* TODO: PMO passages referenced panel */}

            {selectedReport && (
                <div className="ip-report-drawer" style={{ position: 'relative', display: 'block' }}>
                    <div className="ip-drawer-header">
                        <h2>Report: {selectedReport.batchId}</h2>
                        <button className="ip-drawer-close"
                                onClick={() => setSelectedReport(null)}>
                            &times;
                        </button>
                    </div>
                    <div className="ip-drawer-body">
                        <DispositionBadge disposition={selectedReport.disposition} />
                        {/* TODO: Render full report detail */}
                    </div>
                </div>
            )}
        </div>
    );
}
