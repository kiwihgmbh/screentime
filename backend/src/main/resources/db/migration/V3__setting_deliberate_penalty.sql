-- The extra deduction when a parent marks a week as deliberate: a second
-- account, a changed clock. The specification names 60 minutes, and the house
-- rules call it "an extra hour", but it is a value a family will want to argue
-- about, so it lives in the settings table like every other number rather than
-- as a constant in code.

insert into settings (key, value) values ('deliberatePenaltyMinutes', '60');
