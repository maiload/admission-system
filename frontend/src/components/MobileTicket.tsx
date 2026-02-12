import type { TrainSchedule } from '../types';
import korailLogo from '../assets/korail-logo.png';
import qrCode from '../assets/qrcode.png';

interface Props {
  schedule: TrainSchedule;
  confirmationId: string;
  seatLabels: string[];
}

export default function MobileTicket({ schedule, confirmationId, seatLabels }: Props) {
  const normalizedCode = confirmationId.replace(/-/g, '').toUpperCase();
  const displayCode = `${normalizedCode.slice(0, 5)}-${normalizedCode.slice(5, 9)}-${normalizedCode.slice(9, 14)}`;

  const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
  const dateObj = new Date(`${schedule.date}T00:00:00`);
  const dayStr = !isNaN(dateObj.getTime()) ? `(${WEEKDAYS[dateObj.getDay()]})` : '';

  return (
    <div className="bg-white rounded-2xl shadow-xl overflow-hidden max-w-sm mx-auto border border-blue-100">
      {/* Header */}
      <div className="bg-ktx-blue px-5 py-3 flex items-center justify-between">
        <img src={korailLogo} alt="KORAIL" className="h-4 brightness-0 invert" />
        <span className="text-xs text-white/80">{schedule.date} {dayStr}</span>
      </div>

      {/* Route section */}
      <div className="px-8 py-5">
        <div className="flex items-center justify-center gap-8">
          <div className="text-center">
            <p className="text-xl font-bold text-ktx-navy">{schedule.departure}</p>
            <p className="text-sm text-gray-500 mt-0.5">{schedule.departureTime.slice(0, 5)}</p>
          </div>
          <div className="text-gray-300 text-lg">→</div>
          <div className="text-center">
            <p className="text-xl font-bold text-ktx-navy">{schedule.arrival}</p>
            <p className="text-sm text-gray-500 mt-0.5">{schedule.arrivalTime.slice(0, 5)}</p>
          </div>
        </div>
      </div>

      {/* Divider */}
      <div className="border-t border-gray-100 mx-5" />

      {/* Train info */}
      <div className="px-5 py-3 flex items-center justify-between">
        <span className="text-sm font-semibold text-ktx-navy">
          {schedule.trainName} {schedule.trainNumber}
        </span>
        <span className="text-xs border border-gray-300 rounded px-2 py-0.5 text-gray-500">기차정보</span>
      </div>

      {/* Passenger info */}
      <div className="px-5 pb-2 flex items-center justify-between">
        <span className="text-sm text-ktx-navy">
          어른 <span className="text-ktx-blue font-semibold">{seatLabels.length}</span>
        </span>
        <span className="text-xs text-gray-400">승차권 영수증</span>
      </div>

      {/* Details table */}
      <div className="px-5 py-3">
        <table className="w-full text-xs">
          <thead>
            <tr className="text-gray-400 border-b border-gray-100">
              <th className="text-center font-normal pb-2">타는곳</th>
              <th className="text-center font-normal pb-2">호차번호</th>
              <th className="text-center font-normal pb-2">좌석번호</th>
              <th className="text-center font-normal pb-2">승차권</th>
            </tr>
          </thead>
          <tbody>
            <tr className="text-sm text-ktx-navy">
              <td className="py-3 text-center text-xs text-gray-500">15분전<br/>표출</td>
              <td className="py-3 text-center font-semibold">일반실</td>
              <td className="py-3 text-center font-bold">{seatLabels.join(', ')}</td>
              <td className="py-3 flex justify-center">
                <img src={qrCode} alt="QR" className="w-12 h-12 object-contain" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      {/* Ticket number */}
      <div className="px-5 pb-3 text-center">
        <p className="text-xs text-gray-400">
          승차권 번호 : <span className="font-mono">{displayCode}</span>
        </p>
      </div>

      {/* Ticket divider (punch hole effect) */}
      <div className="relative my-1">
        <div className="absolute -left-3 top-1/2 -translate-y-1/2 w-6 h-6 bg-ktx-gray rounded-full" />
        <div className="absolute -right-3 top-1/2 -translate-y-1/2 w-6 h-6 bg-ktx-gray rounded-full" />
        <div className="border-t-2 border-dashed border-gray-200 mx-4" />
      </div>

      {/* Price */}
      <div className="px-5 py-3 text-right">
        <span className="text-2xl font-bold text-ktx-navy">
          {(schedule.price * seatLabels.length).toLocaleString()}
        </span>
        <span className="text-sm text-gray-500 ml-0.5">원</span>
      </div>

      {/* Footer */}
      <div className="bg-ktx-blue px-5 py-2.5 flex items-center justify-between">
        <img src={korailLogo} alt="KORAIL" className="h-3.5 brightness-0 invert" />
        <img src={korailLogo} alt="KORAIL" className="h-3.5 brightness-0 invert" />
        <img src={korailLogo} alt="KORAIL" className="h-3.5 brightness-0 invert" />
      </div>
    </div>
  );
}
