-- The other means of payment (D-89): a debit or credit card, a bank transfer and a QR code, besides cash. The
-- check named for this in V39 is dropped and added again. A refund goes out by its charge's means, so
-- cash_refund does not change.
alter table cash_charge drop constraint ck_cash_charge_means;
alter table cash_charge add constraint ck_cash_charge_means
    check (means in ('CASH', 'DEBIT', 'CREDIT', 'TRANSFER', 'QR'));
