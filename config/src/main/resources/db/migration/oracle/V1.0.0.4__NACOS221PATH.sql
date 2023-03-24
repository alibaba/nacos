alter table config_info modify group_id varchar(128) null;
alter table config_info_aggr modify group_id varchar(128) null;
alter table config_info add encrypted_data_key text not null;
alter table config_info_beta add encrypted_data_key text not null;
alter table his_config_info add encrypted_data_key text not null;




