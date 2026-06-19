-- V4__hb_to_train.sql
-- Now that TRAIN is a committed enum value, reclassify Zürich HB.
UPDATE toilet SET toilet_type = 'TRAIN' WHERE name = 'Zürich HB';
