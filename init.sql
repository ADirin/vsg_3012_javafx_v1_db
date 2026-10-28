-- Time Calculator: translation-table localization (UTF-8 required for ja / ar / fa)
CREATE DATABASE IF NOT EXISTS time_calculator CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE time_calculator;

-- 1. Language-neutral base table
CREATE TABLE IF NOT EXISTS unit (
  id       INT PRIMARY KEY AUTO_INCREMENT,
  code     VARCHAR(20) NOT NULL UNIQUE,   -- 'KM', 'KMH', 'H'
  category VARCHAR(20) NOT NULL           -- 'distance', 'speed', 'time'
) CHARACTER SET utf8mb4;

-- 2. Translation table: one row per unit and language
CREATE TABLE IF NOT EXISTS unit_translation (
  unit_id     INT NOT NULL,
  lang        CHAR(2) NOT NULL,           -- 'en','fi','sv','ja','ar','fa'
  name        VARCHAR(80) NOT NULL,
  description VARCHAR(255),
  PRIMARY KEY (unit_id, lang),
  FOREIGN KEY (unit_id) REFERENCES unit(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4;

-- 3. Existing table, now related to unit (fresh install)
CREATE TABLE IF NOT EXISTS calculation (
  id               INT PRIMARY KEY AUTO_INCREMENT,
  distance         DOUBLE NOT NULL,
  speed            DOUBLE NOT NULL,
  time_hours       DOUBLE NOT NULL,
  language         VARCHAR(5) NOT NULL,
  distance_unit_id INT NOT NULL,
  speed_unit_id    INT NOT NULL,
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (distance_unit_id) REFERENCES unit(id),
  FOREIGN KEY (speed_unit_id)    REFERENCES unit(id)
) CHARACTER SET utf8mb4;

-- ---- MIGRATION: only if `calculation` already exists with the old columns ----
-- ALTER TABLE calculation
--   ADD COLUMN distance_unit_id INT NULL,
--   ADD COLUMN speed_unit_id INT NULL,
--   ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
-- (run the INSERTs below first, then:)
-- UPDATE calculation SET distance_unit_id = (SELECT id FROM unit WHERE code='KM'),
--                        speed_unit_id    = (SELECT id FROM unit WHERE code='KMH');
-- ALTER TABLE calculation
--   MODIFY distance_unit_id INT NOT NULL, MODIFY speed_unit_id INT NOT NULL,
--   ADD FOREIGN KEY (distance_unit_id) REFERENCES unit(id),
--   ADD FOREIGN KEY (speed_unit_id) REFERENCES unit(id);

-- ---- Seed data ----
INSERT IGNORE INTO unit (code, category) VALUES ('KM','distance'), ('KMH','speed'), ('H','time');

INSERT IGNORE INTO unit_translation (unit_id, lang, name, description)
SELECT u.id, v.lang, v.name, v.description FROM unit u JOIN (
  SELECT 'KM' code, 'en' lang, 'kilometre' name, 'Distance in kilometres' description UNION ALL
  SELECT 'KM','fi','kilometri','Matka kilometreinä' UNION ALL
  SELECT 'KM','sv','kilometer','Avstånd i kilometer' UNION ALL
  SELECT 'KM','ja','キロメートル','距離（キロメートル）' UNION ALL
  SELECT 'KM','ar','كيلومتر','المسافة بالكيلومتر' UNION ALL
  SELECT 'KM','fa','کیلومتر','مسافت بر حسب کیلومتر' UNION ALL
  SELECT 'KMH','en','kilometres per hour','Speed in kilometres per hour' UNION ALL
  SELECT 'KMH','fi','kilometriä tunnissa','Nopeus kilometreinä tunnissa' UNION ALL
  SELECT 'KMH','sv','kilometer per timme','Hastighet i kilometer per timme' UNION ALL
  SELECT 'KMH','ja','キロメートル毎時','速度（キロメートル毎時）' UNION ALL
  SELECT 'KMH','ar','كيلومتر في الساعة','السرعة بالكيلومتر في الساعة' UNION ALL
  SELECT 'KMH','fa','کیلومتر بر ساعت','سرعت بر حسب کیلومتر بر ساعت' UNION ALL
  SELECT 'H','en','hour','Time in hours' UNION ALL
  SELECT 'H','fi','tunti','Aika tunteina' UNION ALL
  SELECT 'H','sv','timme','Tid i timmar' UNION ALL
  SELECT 'H','ja','時間','時間（単位：時間）' UNION ALL
  SELECT 'H','ar','ساعة','الوقت بالساعات' UNION ALL
  SELECT 'H','fa','ساعت','زمان بر حسب ساعت'
) v ON v.code = u.code;
