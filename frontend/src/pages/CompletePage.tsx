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
      {/* Success message */}
      <div className="text-center mb-6">
        <div className="w-20 h-20 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#16a34a" strokeWidth="2.5">
            <polyline points="20,6 9,17 4,12" />
          </svg>
        </div>
        <h1 className="text-2xl font-bold text-ktx-navy mb-1">예매가 완료되었습니다!</h1>
        <p className="text-sm text-gray-500">아래 모바일 승차권을 확인해 주세요.</p>
      </div>

      {/* Mobile ticket */}
      <MobileTicket
        schedule={schedule}
        confirmationId={confirmationId}
        seatLabels={seatLabels}
      />

      {/* Actions */}
      <div className="mt-6 space-y-3">
        <button
          onClick={handleHome}
          className="w-full bg-ktx-blue hover:bg-ktx-navy text-white font-semibold py-3 rounded-xl transition-colors cursor-pointer"
        >
          홈으로 돌아가기
        </button>
      </div>
    </div>
  );
}
