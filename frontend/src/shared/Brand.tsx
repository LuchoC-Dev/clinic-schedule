import { Group, Text } from '@mantine/core';

/** The practice's mark and name, at the top of every screen of both applications. */
export function Brand({ size = 18 }: { size?: number }) {
  return (
    <Group gap={12} wrap="nowrap">
      <svg width="28" height="28" viewBox="0 0 28 28" aria-hidden="true">
        <rect x="1" y="1" width="26" height="26" rx="7" fill="#0F5F5B" />
        <path d="M14 7v14M7 14h14" stroke="#fff" strokeWidth="3" strokeLinecap="round" />
      </svg>
      <Text component="span" fw={700} fz={size}>Consultorio</Text>
    </Group>
  );
}
