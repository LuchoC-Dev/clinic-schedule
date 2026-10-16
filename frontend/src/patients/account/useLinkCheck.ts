import { useQuery } from '@tanstack/react-query';

import { ApiError } from '../../shared/api';
import { describeError } from '../../shared/errors';
import { api } from '../session';

/**
 * Whether an email's link still does what it was sent for — activating, choosing a new password, confirming a
 * registration —, asked before the form is shown, as the Thymeleaf screens did (D-110): a link with no token, or one
 * the API refuses, is no use; a server that cannot be reached is said as such.
 */
export function useLinkCheck(path: string, token: string) {
  const check = useQuery({
    queryKey: ['link', path, token],
    enabled: token !== '',
    queryFn: async () => {
      await api.get<void>(`${path}/${encodeURIComponent(token)}`);
      return true;
    },
  });
  const unusable = token === '' || (check.error instanceof ApiError && check.error.status === 400);
  return {
    usable: !unusable && check.isSuccess,
    unusable,
    error: !unusable && check.isError ? describeError(check.error) : null,
  };
}
