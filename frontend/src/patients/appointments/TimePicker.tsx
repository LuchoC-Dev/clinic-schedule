import { Alert, Loader, Stack, Text, UnstyledButton } from '@mantine/core';

import './appointments.css';
import { dayChip } from './appointments';

/**
 * A day and a free time, as the approved mock has it for a phone: the days in a row that scrolls, then the times of
 * the day chosen, three a row. The times are the server's (D-82, D-93); a day with none says so.
 */
export function TimePicker({ days, day, onDay, times, loading, error, time, onTime }: {
  days: string[];
  day: string;
  onDay: (day: string) => void;
  times: string[] | undefined;
  loading: boolean;
  error: string | null;
  time: string | null;
  onTime: (time: string) => void;
}) {
  return (
    <Stack gap={18}>
      <Stack gap={10}>
        <Text fw={700}>Elegí un día</Text>
        <div role="group" aria-label="Días" className="day-chips">
          {days.map((each) => {
            const chip = dayChip(each);
            return (
              <UnstyledButton key={each} className="day-chip" aria-pressed={each === day} onClick={() => onDay(each)}
                aria-label={`${chip.weekday} ${chip.number}`}>
                <span className="day-chip-weekday">{chip.weekday}</span>
                <span className="day-chip-number">{chip.number}</span>
              </UnstyledButton>
            );
          })}
        </div>
      </Stack>
      <Stack gap={10}>
        <Text fw={700}>Horarios libres</Text>
        {error ? <Alert color="red" role="alert">{error}</Alert>
          : loading ? <Loader size="sm" aria-label="Buscando horarios" />
            : times && times.length === 0 ? <Text c="dimmed">No quedan horarios libres ese día: probá con otro.</Text>
              : (
                <div role="group" aria-label="Horarios" className="time-chips">
                  {(times ?? []).map((each) => (
                    <UnstyledButton key={each} className="time-chip" aria-pressed={each === time}
                      onClick={() => onTime(each)}>
                      {each}
                    </UnstyledButton>
                  ))}
                </div>
              )}
      </Stack>
    </Stack>
  );
}
