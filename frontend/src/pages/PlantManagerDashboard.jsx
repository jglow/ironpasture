import React, { useState } from 'react';
import ProcessList from '../components/ProcessList';
import DispositionBadge from '../components/DispositionBadge';

/**
 * PlantManagerDashboard
 *
 * Primary view for plant managers to submit sensor data and receive
 * AI-generated NCIMS 2359 compliance reports.
 */
export default function PlantManagerDashboard() {
    const [formData, setFormData] = useState({
        plantId: 'PMO-WI-4401',
        readingTimestamp: '2026-04-14T06:30',
        pasteurizationTemp: 161.8,
        htstHoldTime: 15.2,
        rawMilkScc: 180000,
        processedMilkSpc: 12000,
        coliformCount: 4,
        ph: 6.72,
        coolerTemp: 38.4,
        phosphataseTest: 'NEGATIVE',
        operatorId: 'OP-2281',
        batchId: 'B-20260414-0630-WI4401',
    });

    const [activeStage, setActiveStage] = useState(null);
    const [result, setResult] = useState(null);
    const [loading, setLoading] = useState(false);

    function handleChange(e) {
        const { name, value } = e.target;
        setFormData((prev) => ({ ...prev, [name]: value }));
    }

    async function handleSubmit(e) {
        e.preventDefault();
        setLoading(true);
        setResult(null);

        // Animate through pipeline stages
        const stages = ['bronze', 'silver', 'gold', 'review'];
        for (let i = 0; i < stages.length; i++) {
            setActiveStage(stages[i]);
            await new Promise((r) => setTimeout(r, 800));
        }

        try {
            const res = await fetch('/api/compliance/pre-fill', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(formData),
            });

            if (!res.ok) throw new Error(`Server returned ${res.status}`);
            const data = await res.json();
            setResult(data);
        } catch (err) {
            console.error('Compliance check failed:', err);
            setResult({ disposition: 'ERROR', narrative: err.message });
        } finally {
            setLoading(false);
            setActiveStage(null);
        }
    }

    return (
        <div>
            <h1 className="ip-page-title">Compliance Dashboard</h1>
            <p className="usa-intro">
                Submit sensor data for automated PMO compliance analysis.
            </p>

            {/* Pipeline stages */}
            <ProcessList activeStage={activeStage} />

            {/* Sensor data form */}
            <section className="ip-form-section">
                <h2>Sensor Data Submission</h2>
                <form className="usa-form usa-form--large ip-sensor-form" onSubmit={handleSubmit}>

                    {/* TODO: Render all sensor fields as controlled inputs */}
                    {/* Fields: plantId, readingTimestamp, pasteurizationTemp, htstHoldTime,
                        rawMilkScc, processedMilkSpc, coliformCount, ph, coolerTemp,
                        phosphataseTest, operatorId, batchId */}

                    <div className="grid-row grid-gap">
                        <div className="grid-col-6">
                            <label className="usa-label" htmlFor="plantId">Plant ID</label>
                            <input className="usa-input" id="plantId" name="plantId"
                                   value={formData.plantId} onChange={handleChange} required />
                        </div>
                        <div className="grid-col-6">
                            <label className="usa-label" htmlFor="batchId">Batch ID</label>
                            <input className="usa-input" id="batchId" name="batchId"
                                   value={formData.batchId} onChange={handleChange} required />
                        </div>
                    </div>

                    {/* Additional fields follow the same pattern */}

                    <button type="submit" className="usa-button ip-btn-submit margin-top-3"
                            disabled={loading}>
                        {loading ? 'Processing\u2026' : 'Run Compliance Check'}
                    </button>
                </form>
            </section>

            {/* Results panel */}
            {result && (
                <section className="ip-results-panel">
                    <h2>Compliance Results</h2>
                    <DispositionBadge disposition={result.disposition} />

                    {/* TODO: Render NCIMS 2359 report table from result.reportLines */}
                    {/* TODO: Render narrative from result.narrative */}
                    {/* TODO: Render PMO citation chips from result.pmoCitations */}

                    {result.narrative && (
                        <div className="ip-narrative-section">
                            <h3>AI-Generated Compliance Narrative</h3>
                            <div className="ip-narrative-panel">
                                <p>{result.narrative}</p>
                            </div>
                        </div>
                    )}
                </section>
            )}
        </div>
    );
}
