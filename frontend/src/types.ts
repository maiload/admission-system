// --- Queue Gate Types ---

export interface SyncResponse {
  serverTimeMs: number;
  startAtMs: number;
  syncToken: string;
  windowMs: number;
}

export interface JoinResponse {
  queueToken: string;
  estimatedRank: number;
  sseUrl: string;
  alreadyJoined: boolean;
}

export interface QueueProgress {
  status: 'WAITING' | 'ADMISSION_GRANTED' | 'EXPIRED' | 'SOLD_OUT';
  estimatedRank: number;
  totalInQueue: number;
  enterToken?: string;
  eventId?: string;
  scheduleId?: string;
  serverTimeMs: number;
}

// --- Ticketing Core Types ---

export interface EnterResponse {
  coreSessionToken: string;
}

export interface Seat {
  seatId: string;
  label: string;
  status: 'AVAILABLE' | 'HELD' | 'CONFIRMED';
}

export interface HoldResponse {
  holdId: string;
  expiresAt: string;
}

export interface ConfirmResponse {
  confirmationId: string;
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
