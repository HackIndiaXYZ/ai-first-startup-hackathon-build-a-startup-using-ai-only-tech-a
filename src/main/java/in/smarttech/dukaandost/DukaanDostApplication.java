package in.smarttech.dukaandost;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DukaanDostApplication {

    @Bean
    Clock applicationClock() {
        return Clock.system(ZoneId.of("Asia/Kolkata"));
    }

    public static void main(String[] args) {
        SpringApplication.run(DukaanDostApplication.class, args);
    }
}
