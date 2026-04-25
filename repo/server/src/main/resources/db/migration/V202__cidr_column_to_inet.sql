-- Change allowed_ip_ranges.cidr from cidr to inet.
-- PostgreSQL's cidr type requires host bits to be zero (e.g. 192.168.1.0/24),
-- which is too strict for storing arbitrary IP-range entries supplied by clients
-- (e.g. 192.168.1.47/24 is valid inet but invalid cidr).  inet stores any
-- host/prefix combination and is the right type here.
ALTER TABLE allowed_ip_ranges ALTER COLUMN cidr TYPE inet USING cidr::inet;
