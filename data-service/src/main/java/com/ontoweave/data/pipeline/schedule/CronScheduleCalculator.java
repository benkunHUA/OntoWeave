package com.ontoweave.data.pipeline.schedule;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class CronScheduleCalculator {

    private static final int UNIX_FIELD_COUNT = 5;
    private static final CronParser PARSER = new CronParser(
            CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    );
    private static final Set<String> ZONE_IDS = ZoneId.getAvailableZoneIds();
    private static final Pattern QUARTZ_DAY_TOKEN = Pattern.compile(
            "(?i)(L|LW|W|L-\\d+|\\d+W)"
    );

    public Instant next(String expression, String timezone, Instant from) {
        ZoneId zone = zone(timezone);
        ExecutionTime execution = execution(expression);
        Instant next = execution.nextExecution(from.atZone(zone))
                .map(ZonedDateTime::toInstant)
                .orElseThrow(() -> new IllegalArgumentException("cron 表达式没有下一次执行时间"));
        return nextRepeatedOverlap(execution, zone, from, next)
                .filter(candidate -> candidate.isBefore(next))
                .orElse(next);
    }

    public Instant latestDue(String expression, String timezone, Instant firstDue, Instant now) {
        if (firstDue == null || now == null || firstDue.isAfter(now)) {
            throw new IllegalArgumentException("到期时间不合法");
        }
        ZoneId zone = zone(timezone);
        ExecutionTime execution = execution(expression);
        ZonedDateTime current = now.atZone(zone);
        if (execution.isMatch(current)) {
            return current.withSecond(0).withNano(0).toInstant();
        }
        return execution.lastExecution(current)
                .map(ZonedDateTime::toInstant)
                .filter(candidate -> !candidate.isBefore(firstDue))
                .orElse(firstDue);
    }

    public void validate(String expression, String timezone) {
        zone(timezone);
        execution(expression);
    }

    private ExecutionTime execution(String expression) {
        String normalized = expression == null ? "" : expression.trim();
        String[] fields = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        if (fields.length != UNIX_FIELD_COUNT) {
            throw new IllegalArgumentException("cron 表达式必须是 5 段 Unix cron");
        }
        if (normalized.contains("?") || normalized.contains("#")) {
            throw new IllegalArgumentException("cron 表达式不支持 Quartz 扩展字符");
        }
        for (String field : fields) {
            if (QUARTZ_DAY_TOKEN.matcher(field).matches()) {
                throw new IllegalArgumentException("cron 表达式不支持 Quartz 扩展字符");
            }
        }
        try {
            Cron cron = PARSER.parse(normalized);
            cron.validate();
            return ExecutionTime.forCron(cron);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("cron 表达式无效", error);
        }
    }

    private Optional<Instant> nextRepeatedOverlap(
            ExecutionTime execution,
            ZoneId zone,
            Instant from,
            Instant ordinaryNext
    ) {
        ZoneRules rules = zone.getRules();
        ZoneOffsetTransition transition = rules.nextTransition(from);
        Instant earliest = null;
        while (transition != null && transition.getInstant().isBefore(ordinaryNext)) {
            if (transition.isOverlap()) {
                Instant repeated = repeatedExecution(execution, zone, transition, from);
                if (repeated != null && (earliest == null || repeated.isBefore(earliest))) {
                    earliest = repeated;
                }
            }
            transition = rules.nextTransition(transition.getInstant());
        }
        return Optional.ofNullable(earliest);
    }

    private Instant repeatedExecution(
            ExecutionTime execution,
            ZoneId zone,
            ZoneOffsetTransition transition,
            Instant from
    ) {
        ZoneOffset before = transition.getOffsetBefore();
        ZoneOffset after = transition.getOffsetAfter();
        LocalDateTime localStart = LocalDateTime.ofInstant(transition.getInstant(), after);
        LocalDateTime localEnd = LocalDateTime.ofInstant(transition.getInstant(), before);
        Instant earliest = null;
        for (LocalDateTime local = localStart;
             local.isBefore(localEnd);
             local = local.plusMinutes(1)) {
            if (!execution.isMatch(local.atZone(zone))) {
                continue;
            }
            earliest = earlierAfter(earliest, local.toInstant(before), from);
            earliest = earlierAfter(earliest, local.toInstant(after), from);
        }
        return earliest;
    }

    private Instant earlierAfter(Instant current, Instant candidate, Instant from) {
        if (!candidate.isAfter(from) || (current != null && !candidate.isBefore(current))) {
            return current;
        }
        return candidate;
    }

    private ZoneId zone(String timezone) {
        if (timezone == null || !ZONE_IDS.contains(timezone)) {
            throw new IllegalArgumentException("时区必须是有效的 IANA 时区");
        }
        return ZoneId.of(timezone);
    }
}
