alter table config_info modify group_id varchar(128);
alter table config_info_aggr modify group_id varchar(128);
alter table config_info add encrypted_data_key varchar2(4000) not null;
alter table config_info_beta add encrypted_data_key varchar2(4000) not null;
alter table his_config_info add encrypted_data_key varchar2(4000) not null;




