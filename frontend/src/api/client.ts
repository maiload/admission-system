import type {
  JoinResponse,
  QueueProgress,
  EnterResponse,
  Seat,
  HoldResponse,
  ConfirmResponse,
  ActiveSchedule,
} from '../types';

const GATE_BASE = '/gate';
const CORE_BASE = '/api';

// --- Queue Gate API ---

export async function joinQueue(
  eventId: string,
  scheduleId: string,
): Promise<JoinResponse> {
  const res = await fetch(`${GATE_BASE}/join`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ eventId, scheduleId }),
  });
  if (!res.ok) {
    const message = await res.text().catch(() => '');
    throw new Error(message || `join failed: ${res.status}`);
  }
  return res.json();
}

export function streamQueue(
  eventId: string,
  scheduleId: string,
  queueToken: string,
  onMessage: (data: QueueProgress) => void,
  onError?: (err: Event) => void,
): EventSource {
  const url = `${GATE_BASE}/stream?eventId=${eventId}&scheduleId=${scheduleId}&queueToken=${queueToken}`;
  const es = new EventSource(url);
  // Backend sends named event "queue.progress"
  es.addEventListener('queue.progress', (event) => {
    const data: QueueProgress = JSON.parse((event as MessageEvent).data);
    onMessage(data);
  });
  es.onerror = (err) => {
    onError?.(err);
  };
  return es;
}

export async function pollStatus(
  eventId: string,
  scheduleId: string,
): Promise<QueueProgress> {
  const res = await fetch(
    `${GATE_BASE}/status?eventId=${eventId}&scheduleId=${scheduleId}`,
    { credentials: 'include' },
  );
  if (!res.ok) throw new Error(`status failed: ${res.status}`);
  return res.json();
}

// --- Ticketing Core API ---

export async function enter(
  eventId: string,
  scheduleId: string,
  enterToken?: string,
): Promise<EnterResponse> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (enterToken) {
    headers['Authorization'] = `Bearer ${enterToken}`;
  }
  const res = await fetch(`${CORE_BASE}/enter`, {
    method: 'POST',
    headers,
    credentials: 'include',
    body: JSON.stringify({ eventId, scheduleId }),
  });
  if (!res.ok) throw new Error(`enter failed: ${res.status}`);
  return res.json();
}

export async function fetchSeats(
  eventId: string,
  scheduleId: string,
): Promise<Seat[]> {
  const res = await fetch(
    `${CORE_BASE}/seats?eventId=${eventId}&scheduleId=${scheduleId}`,
    {
      credentials: 'include',
    },
  );
  if (!res.ok) {
    const message = await res.text().catch(() => '');
    throw new Error(message || `seats failed: ${res.status}`);
  }
  const data = await res.json();
  // Backend returns { zones: [{ zone, seats: [{ seatId, zone, seatNo, status }] }] }
  // Flatten to Seat[] with label
  return data.zones.flatMap((z: { zone: string; seats: { seatId: string; zone: string; seatNo: number; status: string }[] }) =>
    z.seats.map((s) => ({
      seatId: s.seatId,
      label: `${s.seatNo}${s.zone}`,
      status: s.status,
    })),
  );
}

export async function holdSeats(
  eventId: string,
  scheduleId: string,
  seatIds: string[],
): Promise<HoldResponse> {
  const res = await fetch(`${CORE_BASE}/holds`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify({ eventId, scheduleId, seatIds }),
  });
  if (!res.ok) throw new Error(`hold failed: ${res.status}`);
  const data = await res.json();
  return {
    holdGroupId: data.holdGroupId,
    seats: data.seats,
    expiresAt: new Date(data.expiresAtMs).toISOString(),
  };
}

export async function confirmBooking(
  holdGroupId: string,
): Promise<ConfirmResponse> {
  const res = await fetch(`${CORE_BASE}/holds/${holdGroupId}/confirm`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
  });
  if (!res.ok) throw new Error(`confirm failed: ${res.status}`);
  const data = await res.json();
  return {
    confirmationId: data.seats?.[0]?.reservationId ?? '',
    seats: data.seats ?? [],
  };
}

// --- Admin API ---

export async function activateSchedules(): Promise<void> {
  const res = await fetch(`${CORE_BASE}/admin/schedules/activate`, {
    method: 'GET',
    credentials: 'include',
  });
  if (!res.ok) throw new Error(`activate schedules failed: ${res.status}`);
}

export async function fetchActiveSchedules(): Promise<ActiveSchedule[]> {
  const res = await fetch(`${CORE_BASE}/schedules/active`, {
    credentials: 'include',
  });
  if (!res.ok) throw new Error(`active schedules failed: ${res.status}`);
  return res.json();
}
