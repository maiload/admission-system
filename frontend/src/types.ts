// --- Queue Gate Types ---

export interface JoinResponse {
  queueToken: string;
  sseUrl: string;
  alreadyJoined: boolean;
}

export interface QueueProgress {
  status: 'WAITING' | 'ADMISSION_GRANTED' | 'EXPIRED' | 'SOLD_OUT';
  rank: number;
  totalInQueue: number;
  enterToken?: string;
  eventId?: string;
  scheduleId?: string;
}

// --- Ticketing Core Types ---

export interface EnterResponse {
  sessionTtlSec: number;
  eventId: string;
  scheduleId: string;
}

export interface Seat {
  seatId: string;
  label: string;
  status: 'AVAILABLE' | 'HELD' | 'CONFIRMED';
}

export interface HeldSeat {
  seatId: string;
  seatNo: number;
  zone: string;
}

export interface HoldResponse {
  holdGroupId: string;
  seats: HeldSeat[];
  expiresAt: string;
}

export interface ConfirmedSeat {
  reservationId: string;
  seatId: string;
  zone: string;
  seatNo: number;
}

export interface ConfirmResponse {
  confirmationId: string;
  seats: ConfirmedSeat[];
}

// --- UI Types ---

export interface TrainSchedule {
  eventId: string;
  scheduleId: string;
  trainName: string;
  trainNumber: string;
  departure: string;
  arrival: string;
  departureTime: string;
  arrivalTime: string;
  date: string;
  price: number;
}

export interface ActiveSchedule {
  eventId: string;
  scheduleId: string;
  trainName: string;
  trainNumber: string;
  departure: string;
  arrival: string;
  departureTime: string;
  arrivalTime: string;
  serviceDate: string;
  price: number;
}
