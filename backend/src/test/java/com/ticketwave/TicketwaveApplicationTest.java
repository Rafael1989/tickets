package com.ticketwave;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

/**
 * The main() entrypoint is otherwise only exercised by actually booting the
 * app (TicketwaveApplicationIT, which needs Docker/Postgres). Static-mocking
 * SpringApplication proves main() delegates correctly without booting
 * anything.
 */
class TicketwaveApplicationTest {

    @Test
    void main_delegatesToSpringApplicationRunWithThisClassAndTheGivenArgs() {
        String[] args = {"--some-flag=value"};

        try (MockedStatic<SpringApplication> springApplication = Mockito.mockStatic(SpringApplication.class)) {
            TicketwaveApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(TicketwaveApplication.class, args));
        }
    }
}
