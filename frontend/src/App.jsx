import React from 'react';
import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom';
import PlantManagerDashboard from './pages/PlantManagerDashboard';
import InspectorReviewPanel from './pages/InspectorReviewPanel';

/**
 * Iron Pasture — React App Shell
 *
 * This React frontend mirrors the Thymeleaf templates and can serve as
 * a standalone SPA or be used for development iteration. The Thymeleaf
 * templates remain the production frontend served by Spring Boot.
 */
export default function App() {
    return (
        <BrowserRouter>
            {/* Agency Header */}
            <header className="ip-header">
                <div className="grid-container">
                    <div className="grid-row grid-gap flex-align-center">
                        <div className="grid-col-fill">
                            <p className="ip-header__agency">U.S. Department of Agriculture</p>
                            <p className="ip-header__title">
                                Iron Pasture &mdash; Milk Safety Compliance Portal
                            </p>
                        </div>
                        <div className="grid-col-auto">
                            <span className="ip-sovereign-badge">
                                Sovereign AI &mdash; No data leaves this environment
                            </span>
                        </div>
                    </div>
                </div>
            </header>

            {/* Main Content */}
            <div className="grid-container ip-main-container">
                <div className="grid-row grid-gap">
                    <nav className="grid-col-2 ip-sidenav">
                        <ul className="usa-sidenav">
                            <li className="usa-sidenav__item">
                                <NavLink to="/" end>Dashboard</NavLink>
                            </li>
                            <li className="usa-sidenav__item">
                                <NavLink to="/inspector">Inspector Review</NavLink>
                            </li>
                        </ul>
                    </nav>

                    <main className="grid-col-10 ip-content">
                        <Routes>
                            <Route path="/" element={<PlantManagerDashboard />} />
                            <Route path="/inspector" element={<InspectorReviewPanel />} />
                        </Routes>
                    </main>
                </div>
            </div>

            {/* Footer */}
            <footer className="ip-footer">
                <div className="grid-container">
                    <p className="ip-footer__text">
                        Iron Pasture v1.0 | Powered by Tanzu Platform &amp; Tanzu Data Intelligence
                    </p>
                </div>
            </footer>
        </BrowserRouter>
    );
}
