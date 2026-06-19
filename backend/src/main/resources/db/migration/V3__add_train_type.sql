-- V3__add_train_type.sql
-- ALTER TYPE ... ADD VALUE must commit before the new value can be used in DML.
-- The UPDATE to set Zürich HB to TRAIN lives in V4 (separate transaction).
ALTER TYPE toilet_type ADD VALUE IF NOT EXISTS 'TRAIN';
