-- Reference data only. No users: the first parent account is created at
-- startup from APP_ADMIN_USER and APP_ADMIN_PASSWORD.

insert into devices (name, sort_order) values
    ('iPad', 1),
    ('iMac', 2),
    ('Phone', 3),
    ('PlayStation', 4),
    ('TV', 5);

insert into settings (key, value) values
    ('weeklyMinutes', '480'),
    ('weekdayCapMinutes', '60'),
    ('weekendCapMinutes', '120'),
    ('quickDailyMinutes', '15'),
    ('cutoffHour', '20'),
    ('bonusMinutes', '60'),
    ('bonusWeekendCapMinutes', '150'),
    ('maxPenaltyMinutes', '120'),
    ('toleranceMinutes', '10'),
    ('manualMaxMinutes', '240');
