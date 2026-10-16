import { byCode } from '../../shared/refusals';

export const LINK_GONE = 'Este link ya no sirve: el turno se movió o ya se canceló. Si querés cancelarlo, llamá o '
  + 'acercate a recepción.';

/** Cancelling from an email's link, refused in the Thymeleaf screen's words (D-83). */
export const LINK_REFUSALS = byCode({
  NOT_BOOKED: 'Ese turno ya no está reservado.',
  TOO_LATE: 'Falta menos de un día para el turno: para cancelarlo, llamá o acercate a recepción.',
  GONE: LINK_GONE,
});

/** What an email's link would cancel: when, and with whom — nothing else (D-83). */
export interface LinkedAppointment {
  startsAt: string;
  practitioner: string | null;
}
