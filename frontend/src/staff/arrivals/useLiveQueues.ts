import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';

/**
 * The queues as they change, from any screen (D-66): the server's stream says which queue changed, and what was
 * read of it — its session, the practitioner's own queue, and today's agenda, whose arrivals it answers — is read
 * again. Without a browser that streams, the screen is read again when it is opened, as before.
 */
export function useLiveQueues() {
  const queries = useQueryClient();
  useEffect(() => {
    if (typeof EventSource === 'undefined') {
      return undefined;
    }
    const stream = new EventSource('/api/queue-events');
    stream.addEventListener('queue', (event) => {
      void queries.invalidateQueries({ queryKey: ['session', (event as MessageEvent<string>).data] });
      void queries.invalidateQueries({ queryKey: ['my-queue'] });
      void queries.invalidateQueries({ queryKey: ['agenda'] });
    });
    return () => stream.close();
  }, [queries]);
}
