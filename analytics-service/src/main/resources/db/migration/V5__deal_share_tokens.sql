-- A read-only share token per deal: whoever holds it can read the deal's summary and the reports
-- of the sessions inside it, without the fee. Null means the deal is not shared.
alter table deals add column share_token varchar(64);

create unique index idx_deals_share_token on deals (share_token);
