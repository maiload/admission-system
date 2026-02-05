import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useBookingStore } from '../stores/bookingStore';
import { confirmBooking } from '../api/client';

export default function ConfirmPage() {
  const navigate = useNavigate();
  const schedule = useBookingStore((s) => s.schedule);
  const holdId = useBookingStore((s) => s.holdId);
  const expiresAt = useBookingStore((s) => s.expiresAt);
  const selectedSeatIds = useBookingStore((s) => s.selectedSeatIds);
  const seats = useBookingStore((s) => s.seats);
  const selectedSeatLabels = useBookingStore((s) => s.selectedSeatLabels);
  const setConfirmationId = useBookingStore((s) => s.setConfirmationId);

  const [remainSec, setRemainSec] = useState(0);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState('');
  const timerRef = useRef<ReturnType<typeof setInterval>>(null);

  useEffect(() => {
    if (!schedule || !holdId) {
      navigate('/', { replace: true });
      return;
    }

    const expMs = new Date(expiresAt).getTime();
    const updateRemain = () => {
      const diff = Math.max(0, Math.floor((expMs - Date.now()) / 1000));
      setRemainSec(diff);
      if (diff <= 0 && timerRef.current) {
        clearInterval(timerRef.current);
      }
    };
    updateRemain();
    timerRef.current = setInterval(updateRemain, 1000);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [schedule, holdId, expiresAt, navigate]);

  const handleConfirm = async () => {
    if (!holdId) return;
    setConfirming(true);
    setError('');
    try {
      const res = await confirmBooking(holdId);
      setConfirmationId(res.confirmationId);
      navigate('/complete', { replace: true });
    } catch {
      setError('예매 확정에 실패했습니다. 다시 시도해주세요.');
      setConfirming(false);
    }
  };

  if (!schedule) return null;

  const formatTimer = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  const expired = remainSec <= 0;
  const urgent = remainSec > 0 && remainSec <= 30;
  const fallbackLabels = selectedSeatIds.map((id) => {
    const seat = seats.find((s) => s.seatId === id);
    return seat ? seat.label : id.slice(0, 8);
  });
  const displaySeatLabels = selectedSeatLabels.length > 0 ? selectedSeatLabels : fallbackLabels;

  return (
    <div className="max-w-md mx-auto">
      {/* Timer */}
      <div className={`rounded-xl p-4 mb-6 text-center ${expired ? 'bg-red-50 border border-red-200' : urgent ? 'bg-orange-50 border border-orange-200' : 'bg-blue-50 border border-blue-200'}`}>
        <p className="text-xs text-gray-500 mb-1">결제 제한 시간</p>
        <p className={`text-4xl font-bold font-mono ${expired ? 'text-red-600' : urgent ? 'text-ktx-orange' : 'text-ktx-blue'}`}>
          {formatTimer(remainSec)}
        </p>
        {expired && <p className="text-xs text-red-500 mt-1">시간이 만료되었습니다.</p>}
      </div>

      {/* Booking summary card */}
      <div className="bg-white rounded-2xl shadow-lg overflow-hidden mb-6">
        <div className="bg-ktx-blue px-6 py-3 text-white">
          <p className="font-semibold">예매 정보 확인</p>
        </div>

        <div className="px-6 py-5 space-y-4">
          {/* Route */}
          <div className="flex items-center justify-between">
            <div className="text-center">
              <p className="text-xl font-bold text-ktx-navy">{schedule.departure}</p>
              <p className="text-sm text-gray-500">{schedule.departureTime}</p>
            </div>
            <div className="flex-1 mx-4 flex flex-col items-center">
              <p className="text-xs text-ktx-red font-semibold">{schedule.trainName} {schedule.trainNumber}</p>
              <div className="w-full flex items-center mt-1">
                <div className="w-2 h-2 rounded-full bg-ktx-blue" />
                <div className="flex-1 border-t border-dashed border-gray-300 mx-1" />
                <div className="w-2 h-2 rounded-full bg-ktx-red" />
              </div>
            </div>
            <div className="text-center">
              <p className="text-xl font-bold text-ktx-navy">{schedule.arrival}</p>
              <p className="text-sm text-gray-500">{schedule.arrivalTime}</p>
            </div>
          </div>

          <div className="border-t border-gray-100" />

          {/* Details */}
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <p className="text-gray-400">승차일</p>
              <p className="font-semibold text-ktx-navy">{schedule.date}</p>
            </div>
            <div>
              <p className="text-gray-400">좌석</p>
              <p className="font-semibold text-ktx-navy">
                {displaySeatLabels.length}석
              </p>
              {displaySeatLabels.length > 0 && (
                <p className="text-xs text-gray-500 mt-1">
                  {displaySeatLabels.join(', ')}
                </p>
              )}
            </div>
            <div>
              <p className="text-gray-400">요금</p>
              <p className="font-bold text-ktx-red text-lg">
                {(schedule.price * selectedSeatIds.length).toLocaleString()}원
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 mb-4 text-sm text-red-700">
          {error}
        </div>
      )}

      {/* Action buttons */}
      <div className="space-y-3">
        <button
          onClick={handleConfirm}
          disabled={expired || confirming}
          className="w-full bg-ktx-red hover:bg-red-700 disabled:bg-gray-300 text-white font-bold py-4 rounded-xl text-lg transition-colors cursor-pointer disabled:cursor-not-allowed"
        >
          {confirming ? '결제 처리 중...' : expired ? '시간 만료' : '결제하기'}
        </button>
        <button
          onClick={() => navigate('/', { replace: true })}
          className="w-full bg-gray-100 hover:bg-gray-200 text-gray-600 font-semibold py-3 rounded-xl transition-colors cursor-pointer"
        >
          취소
        </button>
      </div>
    </div>
  );
}
