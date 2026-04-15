import React from 'react';

/**
 * DispositionBadge
 *
 * Renders a color-coded chip for compliance disposition values.
 *
 * Props:
 *   disposition: 'COMPLIANT' | 'NON_COMPLIANT' | 'REQUIRES_REVIEW' | string
 */

const DISPOSITION_STYLES = {
    COMPLIANT: {
        className: 'ip-disposition ip-disposition--compliant',
        label: 'COMPLIANT',
    },
    NON_COMPLIANT: {
        className: 'ip-disposition ip-disposition--noncompliant',
        label: 'NON_COMPLIANT',
    },
    REQUIRES_REVIEW: {
        className: 'ip-disposition ip-disposition--review',
        label: 'REQUIRES_REVIEW',
    },
};

export default function DispositionBadge({ disposition }) {
    const style = DISPOSITION_STYLES[disposition] || {
        className: 'ip-disposition',
        label: disposition || 'UNKNOWN',
    };

    return <span className={style.className}>{style.label}</span>;
}
