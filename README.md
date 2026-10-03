# Screentime

A screen time account for a family. One child books their own time, parents
keep the account.

This is not monitoring software. It does not read Apple Screen Time, Google
Family Link or console reports, and it cannot block anything. The built in
limits on the devices do the enforcing. This app does the counting, and it
does the part those limits cannot do: it spans every device, it makes the
remaining budget visible to the child, and it turns the weekly review into a
conversation instead of an argument.

## How it works

- A weekly budget of minutes, with a lower ceiling per day
- Unused time never carries over, not to the next day and not to the next week
- A small separate daily budget for looking things up, free of the weekly budget
- Nothing starts after the evening cut off
- Family film nights do not count
- Once a week, parents compare the log with what the devices report. A week
  that matches earns extra time. Missing time comes off the following week.

Every value is configurable. The defaults were written for an eleven year old.

## Stack

Java 21 and Spring Boot on the backend, Angular on the frontend, PostgreSQL
for storage, Docker for deployment. See `docs/SPEC.md` for the full
specification.

## Running it

    cp .env.example .env     # fill in your own values
    docker compose up --build

The application creates one parent account at first startup from
`APP_ADMIN_USER` and `APP_ADMIN_PASSWORD`. Everything else is created in the
app.

## Status

Early. Built for one family, shared in case it is useful to others. Issues and
pull requests are welcome.

## License

MIT
