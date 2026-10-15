import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { timeAtThePractice } from '../../shared/dates';
import { api } from '../session';
import { cardOf, type RoomQueue } from './room';
import './room.css';

/**
 * The waiting room's screen, on the wall (D-66, D-70, D-109): full screen and without menus, a card for each queue
 * as the approved mock has it — whom it is seeing, highlighted, then who waits, in the order they will be called.
 * It shows itself again as the queues change.
 */
export function WaitingRoomPage({ reload = loadAgain }: { reload?: () => void }) {
  const queues = useQuery({ queryKey: ['waiting-room'], queryFn: () => api.get<RoomQueue[]>('/api/waiting-room') });
  useRoomStream(reload);
  return (
    <div className="room">
      <header className="room-header">
        <div className="room-brand">
          <svg width="56" height="56" viewBox="0 0 28 28" aria-hidden="true">
            <rect x="1" y="1" width="26" height="26" rx="7" fill="#0F5F5B" />
            <path d="M14 7v14M7 14h14" stroke="#fff" strokeWidth="3" strokeLinecap="round" />
          </svg>
          <span className="room-practice">Consultorio</span>
          <h1 className="room-title">Sala de espera</h1>
        </div>
        <Clock />
      </header>
      <main className="room-queues">
        {queues.data?.length === 0 && <p className="room-none">No hay colas.</p>}
        {queues.data?.map((queue) => <QueueCard key={queue.id} queue={queue} />)}
      </main>
    </div>
  );
}

function QueueCard({ queue }: { queue: RoomQueue }) {
  const card = cardOf(queue);
  return (
    <section className={card.closed ? 'room-card closed' : 'room-card'} aria-label={card.title}>
      <div className="room-card-head">
        <h2>{card.title}</h2>
        {card.room && <span>Consultorio {card.room}</span>}
      </div>
      {card.inside.map((entry) => (
        <div key={entry.name} className="room-inside" role="status">
          <span className="room-name">{entry.name}</span>
          <span className="room-go">{entry.label}</span>
        </div>
      ))}
      <div className="room-count">{card.count}</div>
      <ol className="room-waiting" aria-label={`Esperan en ${card.title}`}>
        {card.waiting.map((entry, index) => (
          <li key={index}>
            <span className="room-name">{entry.name}</span>
            <span className="room-place">{entry.label}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}

function loadAgain() {
  window.location.reload();
}

/** The practice's time, large, kept current; only shown. */
function Clock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 15_000);
    return () => window.clearInterval(timer);
  }, []);
  return <span className="room-clock">{timeAtThePractice(now)}</span>;
}

/**
 * The queues as they change (D-66): each change reads the room again. A stream that ends for good — the application
 * restarted, or the session is gone — loads the page again, which goes to the login if nobody is logged in any more,
 * so the wall never shows what it no longer knows.
 */
function useRoomStream(reload: () => void) {
  const queries = useQueryClient();
  useEffect(() => {
    if (typeof EventSource === 'undefined') {
      return undefined;
    }
    const stream = new EventSource('/queue-events');
    stream.addEventListener('queue', () => {
      void queries.invalidateQueries({ queryKey: ['waiting-room'] });
    });
    stream.addEventListener('error', () => {
      if (stream.readyState === EventSource.CLOSED) {
        reload();
      }
    });
    return () => stream.close();
  }, [queries, reload]);
}
