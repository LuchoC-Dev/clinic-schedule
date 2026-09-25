-- The orders a practitioner issued, which they follow up in "Mis órdenes" (D-56).
create index ix_check_order_ordered_by on check_order (ordered_by);
