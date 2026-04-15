import React from 'react';

/**
 * ProcessList
 *
 * USWDS Process List showing the four data pipeline stages:
 * Bronze Ingest -> Silver Enrichment -> Gold Draft -> Inspector Review
 *
 * Props:
 *   activeStage: string | null — currently active stage key
 *     ('bronze' | 'silver' | 'gold' | 'review' | null)
 */

const STAGES = [
    {
        key: 'bronze',
        title: 'Bronze Ingest',
        description:
            'Raw sensor readings are ingested and validated for completeness. Schema enforcement and timestamp normalization applied.',
    },
    {
        key: 'silver',
        title: 'Silver Enrichment',
        description:
            'Readings are enriched with PMO reference ranges, plant metadata, and historical baselines. Outlier detection flags anomalies.',
    },
    {
        key: 'gold',
        title: 'Gold Draft',
        description:
            'Sovereign AI generates NCIMS 2359 compliance narrative and disposition recommendation. PMO passages are cited inline.',
    },
    {
        key: 'review',
        title: 'Inspector Review',
        description:
            'Certified inspector reviews AI-generated report, approves or returns with annotations. Final disposition is recorded in the audit log.',
    },
];

export default function ProcessList({ activeStage }) {
    const activeIndex = STAGES.findIndex((s) => s.key === activeStage);

    return (
        <section className="ip-process-section" aria-label="Compliance pipeline stages">
            <h2>Data Pipeline Stages</h2>
            <ol className="usa-process-list ip-process-list">
                {STAGES.map((stage, i) => {
                    let className = 'usa-process-list__item ip-stage';
                    if (activeStage) {
                        if (i < activeIndex) className += ' ip-stage--complete';
                        else if (i === activeIndex) className += ' ip-stage--active';
                    }

                    return (
                        <li key={stage.key} className={className} data-stage={stage.key}>
                            <h3 className="usa-process-list__heading">{stage.title}</h3>
                            <p className="margin-top-05">{stage.description}</p>
                        </li>
                    );
                })}
            </ol>
        </section>
    );
}
