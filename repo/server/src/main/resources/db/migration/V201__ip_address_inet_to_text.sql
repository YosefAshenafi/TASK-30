ALTER TABLE audit_events   ALTER COLUMN ip_address TYPE TEXT USING ip_address::text;
ALTER TABLE anomaly_events ALTER COLUMN ip_address TYPE TEXT USING ip_address::text;
