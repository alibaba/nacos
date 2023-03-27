alter table config_info alter column group_id type varchar(128) using group_id::varchar(128);
alter table config_info_aggr alter column group_id type varchar(128) using group_id::varchar(128);
alter table config_info add encrypted_data_key text not null;
alter table config_info_beta add encrypted_data_key text not null;
alter table his_config_info add encrypted_data_key text not null;
