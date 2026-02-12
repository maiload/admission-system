import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import HomePage from './pages/HomePage';
import GatePage from './pages/GatePage';
import SeatsPage from './pages/SeatsPage';
import ConfirmPage from './pages/ConfirmPage';
import CompletePage from './pages/CompletePage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/gate" element={<GatePage />} />
          <Route path="/seats" element={<SeatsPage />} />
          <Route path="/confirm" element={<ConfirmPage />} />
          <Route path="/complete" element={<CompletePage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
