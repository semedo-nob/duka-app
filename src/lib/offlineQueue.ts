const KEY = 'duka.offlineSales';

export type QueuedSale = {
  idempotencyKey: string;
  body: unknown;
  at: string;
};

export function readQueue(): QueuedSale[] {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as QueuedSale[]) : [];
  } catch {
    return [];
  }
}

export function enqueueSale(entry: QueuedSale) {
  const next = readQueue().filter((row) => row.idempotencyKey !== entry.idempotencyKey);
  next.push(entry);
  localStorage.setItem(KEY, JSON.stringify(next));
}

export function removeQueued(idempotencyKey: string) {
  localStorage.setItem(KEY, JSON.stringify(readQueue().filter((row) => row.idempotencyKey !== idempotencyKey)));
}
