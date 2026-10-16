import { Alert, Button, Loader, Paper, Stack, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { describeError } from '../../shared/errors';
import { Said } from '../../shared/Said';
import { useAction } from '../../shared/useAction';
import { api } from '../session';
import { dayAndTime } from '../words';
import { LINK_GONE, LINK_REFUSALS, type LinkedAppointment } from './cancel';

/**
 * The page of a reminder's or a confirmation's link, with nobody logged in (D-83, D-110): when and with whom — what
 * the email already says, nothing of the patient's —, and a button.
 */
export function CancelLinkPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const path = `/api/portal/cancel-links/${encodeURIComponent(token)}`;
  const linked = useQuery({
    queryKey: ['cancel-link', token],
    enabled: token !== '',
    queryFn: () => api.get<LinkedAppointment>(path),
  });
  const cancel = useAction(() => api.post(`${path}/cancel`), {
    refusals: LINK_REFUSALS,
    done: 'Turno cancelado. Si querés otro, sacalo desde tu cuenta o llamá a recepción.',
  });

  const refused = token === '' ? LINK_GONE : linked.isError ? describeError(linked.error, LINK_REFUSALS) : null;
  return (
    <Stack gap={16}>
      <Title order={1} fz={26}>Cancelar turno</Title>
      {refused ? <Alert color="red" role="alert">{refused}</Alert>
        : cancel.notice || cancel.error ? <Said error={cancel.error} notice={cancel.notice} />
          : !linked.data ? <Loader size="sm" aria-label="Buscando el turno" />
            : (
              <Paper className="card" p={18}>
                <Stack gap={14}>
                  <Text fz={17}>Turno del <strong>{dayAndTime(linked.data.startsAt)}</strong>
                    {linked.data.practitioner && <> con <strong>{linked.data.practitioner}</strong></>}.</Text>
                  <Button color="red" size="md" loading={cancel.pending} onClick={() => cancel.run(undefined)}>
                    Cancelar el turno
                  </Button>
                </Stack>
              </Paper>
            )}
    </Stack>
  );
}
