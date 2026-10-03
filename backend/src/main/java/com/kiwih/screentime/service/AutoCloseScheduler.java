package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.Session;
import com.kiwih.screentime.rules.Balance;
import com.kiwih.screentime.rules.ScreentimeRules;
import com.kiwih.screentime.rules.WeekState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Closes whatever is still running at 23:59 local time.
 *
 * The charge is capped at what the day had left, so one forgotten stop cannot
 * wipe out a week. The row is flagged {@code autoClosed} and the parents decide
 * whether the number is fair: the app will not quietly take eight hours off a
 * child who left an iPad on.
 *
 * A session left open from an earlier day, because the app was down, is closed
 * at 23:59 of the day it started, not today.
 */
@Component
public class AutoCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoCloseScheduler.class);

    private final SessionService sessions;
    private final SettingsService settingsService;
    private final BalanceService balances;
    private final WeekService weeks;

    public AutoCloseScheduler(SessionService sessions, SettingsService settingsService,
                              BalanceService balances, WeekService weeks) {
        this.sessions = sessions;
        this.settingsService = settingsService;
        this.balances = balances;
        this.weeks = weeks;
    }

    @Scheduled(cron = "0 59 23 * * *", zone = "${app.timezone}")
    @Transactional
    public void closeForgottenSessions() {
        int closed = closeAllOpen();
        if (closed > 0) {
            log.info("Closed {} session(s) that were still running at 23:59.", closed);
        }
    }

    /** Returns how many sessions were closed. Visible for tests. */
    @Transactional
    public int closeAllOpen() {
        int closed = 0;

        for (Session open : sessions.allOpenSessions()) {
            LocalDate startDay = settingsService.calendar().dayOf(open.getStartedAt());
            // the session is charged against the week it started in, with that week's values
            ScreentimeRules rules = settingsService.rulesFor(startDay);
            LocalDate weekStart = rules.calendar().weekStartOf(startDay);
            WeekState week = weeks.state(weekStart);
            Instant closeAt = rules.calendar().autoCloseMoment(startDay);

            Balance before = balances.balanceOfClosedSessions(
                    open.getUserId(), startDay, week, rules);
            int seconds = rules.autoCloseSeconds(
                    open.getType(), open.getStartedAt(), closeAt, before);

            sessions.closeAutomatically(open, closeAt, seconds, null);
            log.info("Auto closed session {} on {} with {} seconds.", open.getId(), startDay, seconds);
            closed++;
        }
        return closed;
    }
}
