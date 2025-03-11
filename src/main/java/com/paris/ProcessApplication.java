package com.paris;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.core.env.Environment;
import org.springframework.core.metrics.StartupStep;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;

@SpringBootApplication
@Slf4j
public class ProcessApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ProcessApplication.class);
        BufferingApplicationStartup startup = new BufferingApplicationStartup(1000);
        app.setApplicationStartup(startup);
        Environment env = app.run(args).getEnvironment();
        StartupTimeline bufferedTimeline = startup.getBufferedTimeline();
        StringBuilder sb = new StringBuilder();
        sb.append("Refresh Context 步骤分析：\n");
        for (StartupTimeline.TimelineEvent event : bufferedTimeline.getEvents()) {
            Instant startTime = event.getStartTime();
            Instant endTime = event.getEndTime();
            Duration duration = event.getDuration();
            StartupStep startupStep = event.getStartupStep();
            String name = startupStep.getName();

            sb.append("步骤: ").append(name)
                    .append("，开始时间: ").append(startTime)
                    .append("，结束时间: ").append(endTime)
                    .append("，耗时: ").append(duration.toMillis()).append("ms");

            // 处理 StartupStep 的 Tag
            StartupStep.Tags tags = startupStep.getTags();
            Iterator<StartupStep.Tag> iterator = tags.iterator();
            if (iterator.hasNext()){
                StartupStep.Tag tag = iterator.next();
                sb.append("，标签: {");
                sb.append(tag.getKey()).append(": ").append(tag.getValue()).append(", ");
            }
            sb.setLength(sb.length() - 2); // 移除最后的 ", "
            sb.append("}");
            sb.append("\n");
        }
        log.info(sb.toString());
        String protocol = "http";
        if (env.getProperty("server.ssl.key-store") != null) {
            protocol = "https";
        }

        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (
                Exception e) {
            log.warn("The host name could not be determined, using `localhost` as fallback");
        }
        log.info("\n======================================================\n\t" +
                        "Application '{}' is running! Access URLs:\n\t" +
                        "Local: \t\t{}://localhost:{}\n\t" +
                        "External: \t{}://{}:{}\n\t" +
                        "Profile(s): \t{}\n=================Started successfully=================",
                env.getProperty("spring.application.name"),
                protocol,
                env.getProperty("server.port"),

                protocol,
                hostAddress,
                env.getProperty("server.port"),
                env.getActiveProfiles());
    }

}