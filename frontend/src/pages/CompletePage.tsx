import { useNavigate } from 'react-router-dom';
import { useBookingStore } from '../stores/bookingStore';
import MobileTicket from '../components/MobileTicket';

export default function CompletePage() {
  const navigate = useNavigate();
  const schedule = useBookingStore((s) => s.schedule);
  const confirmationId = useBookingStore((s) => s.confirmationId);
  const selectedSeatIds = useBookingStore((s) => s.selectedSeatIds);
  const seats = useBookingStore((s) => s.seats);
  const selectedSeatLabels = useBookingStore((s) => s.selectedSeatLabels);
  const reset = useBookingStore((s) => s.reset);

  if (!schedule || !confirmationId) {
    navigate('/', { replace: true });
    return null;
  }

  const fallbackLabels = selectedSeatIds.map((id) => {
    const seat = seats.find((s) => s.seatId === id);
    return seat?.label ?? id;
  });
  const seatLabels = selectedSeatLabels.length > 0 ? selectedSeatLabels : fallbackLabels;

  const handleHome = () => {
    reset();
    navigate('/', { replace: true });
  };

  return (
    <div className="max-w-md mx-auto">
      {/* Success badge */}
      <div className="text-center mb-5">
        <div className="w-14 h-14 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-3">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#16a34a" strokeWidth="3">
            <polyline points="20,6 9,17 4,12" />
          </svg>
        </div>
        <p className="text-base font-bold text-ktx-navy">예매가 완료되었습니다</p>
      </div>

      {/* Mobile ticket */}
      <MobileTicket
        schedule={schedule}
        confirmationId={confirmationId}
        seatLabels={seatLabels}
      />

      {/* Actions */}
      <div className="mt-5 flex gap-3">
        <button
          onClick={handleHome}
          className="flex-1 bg-ktx-blue hover:bg-ktx-navy text-white font-semibold py-3 rounded-xl transition-colors cursor-pointer text-sm"
        >
          홈으로
        </button>
        <button
          onClick={handleHome}
          className="flex-1 border border-gray-300 hover:bg-gray-50 text-gray-600 font-semibold py-3 rounded-xl transition-colors cursor-pointer text-sm"
        >
          결제내역
        </button>
      </div>
    </div>
  );
}
