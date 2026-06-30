-- Baseline schema for sportshub-backend (MySQL 8).
--
-- Generated from the JPA entity metadata via Hibernate's MySQLDialect schema
-- export, so it matches what `ddl-auto: validate` expects on boot. Hibernate's
-- auto-generated constraint names (UK*/FK*) are kept verbatim for parity.
--
-- Manual fixes applied to the generated output:
--   * `match` (table) and `index` (column on round) are MySQL reserved words —
--     backticked here; Hibernate emitted them unquoted, which would fail CREATE.
-- Future schema changes go in new V2__*.sql / V3__*.sql files — never edit this one.

create table category (id varchar(14) not null, name varchar(255), short_name varchar(255), primary key (id)) engine=InnoDB;
create table club (active bit not null, id varchar(14) not null, city varchar(255), federation_id varchar(255) not null, name varchar(255) not null, short_name varchar(255), primary key (id)) engine=InnoDB;
create table competition (id varchar(14) not null, season_id varchar(14), import_id varchar(255), name varchar(255), primary key (id)) engine=InnoDB;
create table discipline (category_id varchar(14), competition_id varchar(14), id varchar(14) not null, primary key (id)) engine=InnoDB;
create table federation (id varchar(14) not null, name varchar(255), primary key (id)) engine=InnoDB;
create table location (federation_id varchar(14), id varchar(14) not null, address varchar(255), name varchar(255), primary key (id)) engine=InnoDB;
create table `match` (away_score integer, home_score integer, end_time datetime(6), start_time datetime(6) not null, id varchar(14) not null, match_day_id varchar(14), state enum ('BYE','INCOMPLETE','OPEN','PAUSED','PLANNED','PLAYED','RUNNING','SKIPPED'), type enum ('DOUBLE','GOALIE','SINGLE','TRIPLE'), winner enum ('AWAY','DRAW','HOME'), primary key (id)) engine=InnoDB;
create table match_day (away_confirmed_at datetime(6), end_date datetime(6), home_confirmed_at datetime(6), start_date datetime(6) not null, id varchar(14) not null, location_id varchar(14), round_id varchar(14), team_away_id varchar(14), team_home_id varchar(14), name varchar(255), submitted_by_dtfb_id varchar(255), result_state enum ('CONFIRMED','HOME_SUBMITTED','OPEN') not null, primary key (id)) engine=InnoDB;
create table match_event (away_score integer, home_score integer, timestamp datetime(6), id varchar(14) not null, match_id varchar(14), team_id varchar(14), player_id varchar(255), json longtext, type enum ('CARD','END','GOAL','OTHER','OWN_GOAL','START','TIMEOUT'), primary key (id)) engine=InnoDB;
create table match_set (away_score integer, home_score integer, set_number integer, id varchar(14) not null, match_id varchar(14), primary key (id)) engine=InnoDB;
create table player (active bit not null, birth_year integer, id varchar(14) not null, dtfb_id varchar(255) not null, email varchar(255), first_name varchar(255), gender varchar(255), international_id varchar(255), last_name varchar(255), national_id varchar(255), national_license varchar(255), nationality varchar(255), primary key (id)) engine=InnoDB;
create table pool (id varchar(14) not null, stage_id varchar(14), name varchar(255), pool_state enum ('CANCELED','FINISHED','PLANNED','READY','RUNNING') not null, tournament_mode enum ('DOUBLE_ELIMINATION','DUTCH_SYSTEM','ELIMINATION','LAST_ONE_STANDING','LORD_HAVE_MERCY','MONSTER_DYP','ROUNDS','ROUND_ROBIN','SNAKE_DRAW','SWISS','UNKNOWN','WHIST') not null, primary key (id)) engine=InnoDB;
create table role_assignment (created_at datetime(6) not null, id varchar(14) not null, player_id varchar(14) not null, granted_by_dtfb_id varchar(255), scope_id varchar(255), role enum ('ADMIN','CLUB_ADMIN','COMPETITION_ORGANIZER','REGION_ADMIN','REGION_TOURNAMENT_UPLOADER','TEAM_ADMIN','TOURNAMENT_UPLOADER') not null, scope_type enum ('CLUB','COMPETITION','GLOBAL','REGION','TEAM') not null, primary key (id)) engine=InnoDB;
create table round (`index` integer, id varchar(14) not null, pool_id varchar(14), name varchar(255), primary key (id)) engine=InnoDB;
create table season (end_date date, registration_open bit not null, start_date date, registration_closes_at datetime(6), registration_opens_at datetime(6), federation_id varchar(14), id varchar(14) not null, name varchar(255), primary key (id)) engine=InnoDB;
create table stage (discipline_id varchar(14), id varchar(14) not null, name varchar(255), primary key (id)) engine=InnoDB;
create table standing (draws integer not null, losses integer not null, played integer not null, points integer not null, sets_lost integer not null, sets_won integer not null, wins integer not null, id varchar(14) not null, pool_id varchar(14) not null, team_id varchar(14) not null, primary key (id)) engine=InnoDB;
create table team (club_id varchar(14), id varchar(14) not null, name varchar(255), primary key (id)) engine=InnoDB;

alter table player add constraint UKmudwa1n9gtkvobjk5ccc97vtl unique (dtfb_id);
alter table standing add constraint UKqxny1tg9g6vqa0e0u46j3e646 unique (pool_id, team_id);

alter table competition add constraint FKdxy6w1o5algqnmlguqn01v7b3 foreign key (season_id) references season (id);
alter table discipline add constraint FKcp0qkjj3345ooja5nh8b8yu3n foreign key (category_id) references category (id);
alter table discipline add constraint FKrstli1ajfrm2mhskjt9547lek foreign key (competition_id) references competition (id);
alter table location add constraint FKi6n5a7tcvrr3uc3qhqd64ihqc foreign key (federation_id) references federation (id);
alter table `match` add constraint FK2qmy5muu9vxx4512nxsthkjxp foreign key (match_day_id) references match_day (id);
alter table match_day add constraint FKtk4ted7itedgwlf7n7lk3evjx foreign key (location_id) references location (id);
alter table match_day add constraint FK9arahbxedbti468tvy7po2yn5 foreign key (round_id) references round (id);
alter table match_day add constraint FKe0gkxxw4ph39nqpnb33q3trrr foreign key (team_away_id) references team (id);
alter table match_day add constraint FKl1seeuayqtf7naq5v2e186764 foreign key (team_home_id) references team (id);
alter table match_event add constraint FKe7ks4tsqbgjkauai7u8ah9orv foreign key (match_id) references `match` (id);
alter table match_event add constraint FKl8mesg52cgwimxc5nlqdj89t7 foreign key (team_id) references team (id);
alter table match_set add constraint FKofmrkpcwi51eb1ar71pwacdhb foreign key (match_id) references `match` (id);
alter table pool add constraint FKgme6o3yoadiutgi2a52bqy4bw foreign key (stage_id) references stage (id);
alter table role_assignment add constraint FKiuagbx71ubywl5uvru90u5t8w foreign key (player_id) references player (id);
alter table round add constraint FKgw33qg025namrrl0e1956fadr foreign key (pool_id) references pool (id);
alter table season add constraint FKhkq1tyg39jwy19xveluigi6o2 foreign key (federation_id) references federation (id);
alter table stage add constraint FKptax13vt5flogh0ybstbppri foreign key (discipline_id) references discipline (id);
alter table standing add constraint FKdglxqsri6mlme568q4p9ri0a1 foreign key (pool_id) references pool (id);
alter table standing add constraint FKk0dfm7sbpoa92k84uygyb1iax foreign key (team_id) references team (id);
alter table team add constraint FKnl01gosacvic5nvy7gq3h7v2y foreign key (club_id) references club (id);
