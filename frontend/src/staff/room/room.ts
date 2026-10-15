/**
 * A queue as the waiting room shows it (D-109): whether it is open, whom its practitioners are seeing, and who waits,
 * in the order they will be called, each by their full name — the alias of one who gave none.
 */
export interface RoomQueue {
  id: string;
  name: string;
  room: string | null;
  open: boolean;
  called: string[];
  waiting: string[];
}

export interface Card {
  title: string;
  room: string | null;
  closed: boolean;
  inside: { name: string; label: string }[];
  count: string;
  waiting: { name: string; label: string }[];
}

/** `Pasá al consultorio 3`, or `Pasá` for a queue with no room. */
function goIn(room: string | null): string {
  return room ? `Pasá al consultorio ${room}` : 'Pasá';
}

/** `Sigue` for the next, then `2°`, `3°`… as the approved mock has them. */
export function place(index: number): string {
  return index === 0 ? 'Sigue' : `${index + 1}°`;
}

/** How many wait: `Nadie esperando`, `1 esperando`, `3 esperando`. */
export function counted(waiting: number): string {
  return waiting === 0 ? 'Nadie esperando' : `${waiting} esperando`;
}

/** A queue's card: whom it is seeing, highlighted, then who waits, in order; a closed one says only that. */
export function cardOf(queue: RoomQueue): Card {
  if (!queue.open) {
    return { title: queue.name, room: queue.room, closed: true, inside: [], count: 'Cerrada', waiting: [] };
  }
  return {
    title: queue.name,
    room: queue.room,
    closed: false,
    inside: queue.called.map((name) => ({ name, label: goIn(queue.room) })),
    count: counted(queue.waiting.length),
    waiting: queue.waiting.map((name, index) => ({ name, label: place(index) })),
  };
}
