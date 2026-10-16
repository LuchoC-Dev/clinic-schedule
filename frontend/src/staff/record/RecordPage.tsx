import { Anchor, Stack } from '@mantine/core';
import { Link, useLocation, useParams } from 'react-router';

import { Said } from '../../shared/Said';
import { RecordPanel } from './RecordPanel';

/**
 * A patient's clinical record on its own, reached from the agenda, the queue or the lists (D-104), saying until when
 * access was granted when that is how it was reached (D-105).
 */
export function RecordPage() {
  const { patientId = '' } = useParams();
  const notice = (useLocation().state as { notice?: string } | null)?.notice ?? null;
  return (
    <Stack gap={16}>
      <Anchor component={Link} to="/my-queue" fz={14}>‹ Mi cola</Anchor>
      <Said notice={notice} />
      <RecordPanel key={patientId} patientId={patientId} />
    </Stack>
  );
}
