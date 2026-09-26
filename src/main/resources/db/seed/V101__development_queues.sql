-- Development data (D-34): a queue for each seeded practitioner, so a patient with an appointment can
-- arrive right after starting the application (D-65). A queue is its events (D-62): each one is set
-- up as the application would, with its events and the projection they add up to.
--
-- A database already in use may have queues of its own: a queue is only added for a practitioner who
-- serves none, under a name nobody has taken, and its events and membership only when it was added.

insert into arrival_queue (id, name, name_key, room)
select cast('33333333-3333-3333-3333-333333333333' as uuid), 'Consultorio de Arias', 'consultorio de arias', '1'
where not exists (select 1 from queue_membership where practitioner_id = cast('11111111-1111-1111-1111-111111111111' as uuid))
  and not exists (select 1 from arrival_queue where name_key = 'consultorio de arias');

insert into arrival_queue (id, name, name_key, room)
select cast('44444444-4444-4444-4444-444444444444' as uuid), 'Consultorio de Benítez', 'consultorio de benítez', '2'
where not exists (select 1 from queue_membership where practitioner_id = cast('22222222-2222-2222-2222-222222222222' as uuid))
  and not exists (select 1 from arrival_queue where name_key = 'consultorio de benítez');

insert into queue_membership (practitioner_id, queue_id)
select cast('11111111-1111-1111-1111-111111111111' as uuid), id from arrival_queue
where id = cast('33333333-3333-3333-3333-333333333333' as uuid);

insert into queue_membership (practitioner_id, queue_id)
select cast('22222222-2222-2222-2222-222222222222' as uuid), id from arrival_queue
where id = cast('44444444-4444-4444-4444-444444444444' as uuid);

insert into arrival_event (stream_id, version, event_type, payload, recorded_at, recorded_by)
select id, 1, 'QueueCreated', '{"name":"Consultorio de Arias","room":"1"}', cast('2026-09-26 00:00:00' as timestamp), 'recepcion'
from arrival_queue where id = cast('33333333-3333-3333-3333-333333333333' as uuid);

insert into arrival_event (stream_id, version, event_type, payload, recorded_at, recorded_by)
select id, 2, 'PractitionerJoined', '{"practitionerId":"11111111-1111-1111-1111-111111111111"}', cast('2026-09-26 00:00:00' as timestamp), 'recepcion'
from arrival_queue where id = cast('33333333-3333-3333-3333-333333333333' as uuid);

insert into arrival_event (stream_id, version, event_type, payload, recorded_at, recorded_by)
select id, 1, 'QueueCreated', '{"name":"Consultorio de Benítez","room":"2"}', cast('2026-09-26 00:00:00' as timestamp), 'recepcion'
from arrival_queue where id = cast('44444444-4444-4444-4444-444444444444' as uuid);

insert into arrival_event (stream_id, version, event_type, payload, recorded_at, recorded_by)
select id, 2, 'PractitionerJoined', '{"practitionerId":"22222222-2222-2222-2222-222222222222"}', cast('2026-09-26 00:00:00' as timestamp), 'recepcion'
from arrival_queue where id = cast('44444444-4444-4444-4444-444444444444' as uuid);
