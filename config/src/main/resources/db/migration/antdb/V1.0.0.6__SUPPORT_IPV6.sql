alter table config_info_tag alter column src_ip type varchar(50) using src_ip::varchar(50);
alter table his_config_info alter column src_ip type varchar(50) using src_ip::varchar(50);
alter table config_info alter column src_ip type varchar(50) using src_ip::varchar(50);
alter table config_info_beta alter column src_ip type varchar(50) using src_ip::varchar(50);