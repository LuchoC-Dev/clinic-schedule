import { Anchor, Stack } from '@mantine/core';
import { Link, useParams } from 'react-router';

import { RecordPanel } from './RecordPanel';

/** A patient's clinical record on its own, reached from the agenda, the queue or the lists (D-104). */
export function RecordPage() {
  const { patientId = '' } = useParams();
  return (
    <Stack gap={16}>
      <Anchor component={Link} to="/my-queue" fz={14}>‹ Mi cola</Anchor>
      <RecordPanel key={patientId} patientId={patientId} />
    </Stack>
  );
}
