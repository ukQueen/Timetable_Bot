package com.timetablebot.application.llm;

import com.timetablebot.domain.schedule.ScheduleEvent;
import com.timetablebot.domain.user.TaskItem;
import com.timetablebot.infrastructure.llm.LlmClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class LlmAdvisorModule {

    private static final String SYSTEM_PROMPT = """
            Ты — умный учебный советник для студентов. Ты помогаешь расставить приоритеты в учёбе,
            составляешь планы подготовки к экзаменам и отвечаешь на вопросы по планированию.
            Отвечай кратко, конкретно и на русском языке. Используй маркированные списки где уместно.
            """;

    private final LlmClient llmClient;

    public LlmAdvisorModule(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public Mono<String> askFreeForm(String question) {
        return llmClient.ask(SYSTEM_PROMPT, question);
    }

    public Mono<String> advisePriority(List<TaskItem> tasks, List<ScheduleEvent> upcomingEvents) {
        String userMessage = buildPriorityContext(tasks, upcomingEvents);
        return llmClient.ask(SYSTEM_PROMPT, userMessage);
    }

    public Mono<String> generateStudyPlan(String subject, String examDateIso, String additionalInfo) {
        String userMessage = String.format(
                "Составь план подготовки к экзамену по предмету: %s. Дата экзамена: %s.%s",
                subject,
                examDateIso,
                additionalInfo == null || additionalInfo.isBlank() ? "" : "\nДополнительно: " + additionalInfo
        );
        return llmClient.ask(SYSTEM_PROMPT, userMessage);
    }

    private String buildPriorityContext(List<TaskItem> tasks, List<ScheduleEvent> events) {
        StringBuilder sb = new StringBuilder("Помоги мне расставить приоритеты в учёбе.\n\n");

        if (!tasks.isEmpty()) {
            sb.append("Мои текущие задачи:\n");
            for (TaskItem task : tasks) {
                sb.append("- ").append(task.title())
                        .append(" (дедлайн: ").append(task.deadline())
                        .append(", приоритет: ").append(task.priority())
                        .append(", тип: ").append(task.type()).append(")\n");
            }
            sb.append("\n");
        }

        if (!events.isEmpty()) {
            sb.append("Ближайшие занятия и экзамены:\n");
            for (ScheduleEvent event : events) {
                sb.append("- ").append(event.type()).append(": ").append(event.title())
                        .append(" (").append(event.startsAt()).append(")\n");
            }
            sb.append("\n");
        }

        sb.append("Что важнее всего сделать в первую очередь?");
        return sb.toString();
    }
}
