package com.ticketwave.partner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.ticketwave.partner.entity.Partner;
import com.ticketwave.partner.entity.PartnerWebhook;
import com.ticketwave.partner.entity.WebhookDeliveryLog;
import com.ticketwave.partner.entity.WebhookStatus;
import com.ticketwave.partner.repository.PartnerWebhookRepository;
import com.ticketwave.partner.repository.WebhookDeliveryLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Calls deliver() directly on a plain instance (not a Spring proxy), so
 * @Async has no effect here — the call runs synchronously on the test
 * thread, which is exactly what's wanted to assert on its outcome
 * immediately rather than needing to await background completion.
 */
@ExtendWith(MockitoExtension.class)
class PartnerWebhookDeliveryServiceTest {

    @Mock
    private PartnerWebhookRepository webhookRepository;
    @Mock
    private WebhookDeliveryLogRepository deliveryLogRepository;

    private PartnerWebhookDeliveryService service;

    private HttpServer server;

    @BeforeEach
    void createService() {
        service = new PartnerWebhookDeliveryService(webhookRepository, deliveryLogRepository, new ObjectMapper());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private record Payload(Long bookingId, String pnr) {
    }

    private static PartnerWebhook webhook(String url) {
        return PartnerWebhook.builder().id(1L).partner(Partner.builder().id(9L).build())
                .url(url).secret("test-secret").eventType("BOOKING_CANCELLED").status(WebhookStatus.ACTIVE).build();
    }

    @Test
    void deliver_withNoActiveWebhooksForTheEvent_doesNothing() {
        given(webhookRepository.findByPartnerIdAndEventTypeAndStatus(9L, "BOOKING_CANCELLED", WebhookStatus.ACTIVE))
                .willReturn(List.of());

        service.deliver(9L, "BOOKING_CANCELLED", new Payload(500L, "ABC123"));

        verify(deliveryLogRepository, never()).save(any());
    }

    @Test
    void deliver_toAReachableEndpoint_signsThePayloadAndLogsSuccess() throws Exception {
        int port = freePort();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedSignature = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/hook", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedSignature.set(exchange.getRequestHeaders().getFirst("X-TicketWave-Signature"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        PartnerWebhook webhook = webhook("http://127.0.0.1:" + port + "/hook");
        given(webhookRepository.findByPartnerIdAndEventTypeAndStatus(9L, "BOOKING_CANCELLED", WebhookStatus.ACTIVE))
                .willReturn(List.of(webhook));

        service.deliver(9L, "BOOKING_CANCELLED", new Payload(500L, "ABC123"));

        assertThat(receivedBody.get()).contains("\"bookingId\":500").contains("\"pnr\":\"ABC123\"");
        assertThat(receivedSignature.get()).startsWith("sha256=");

        ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        assertThat(captor.getValue().isSuccess()).isTrue();
        assertThat(captor.getValue().getResponseStatus()).isEqualTo(200);
    }

    @Test
    void deliver_toAnUnreachableEndpoint_retriesOnceThenLogsFailure() {
        int closedPort = freePort(); // nothing is listening here
        PartnerWebhook webhook = webhook("http://127.0.0.1:" + closedPort + "/hook");
        given(webhookRepository.findByPartnerIdAndEventTypeAndStatus(9L, "BOOKING_CANCELLED", WebhookStatus.ACTIVE))
                .willReturn(List.of(webhook));

        service.deliver(9L, "BOOKING_CANCELLED", new Payload(500L, "ABC123"));

        ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        assertThat(captor.getValue().isSuccess()).isFalse();
        assertThat(captor.getValue().getErrorMessage()).isNotBlank();
    }

    @Test
    void deliver_toAnEndpointReturningANonSuccessStatus_logsFailureWithThatStatus() throws Exception {
        int port = freePort();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        // 304 is the interesting case: RestClient's default status handler
        // only raises for 4xx/5xx, so a 3xx comes back as an ordinary
        // response and the "did it succeed?" decision rests entirely on the
        // status < 300 check. Treating it as success would mark a webhook
        // the partner never accepted as delivered.
        server.createContext("/hook", exchange -> {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
        });
        server.start();

        PartnerWebhook webhook = webhook("http://127.0.0.1:" + port + "/hook");
        given(webhookRepository.findByPartnerIdAndEventTypeAndStatus(9L, "BOOKING_CANCELLED", WebhookStatus.ACTIVE))
                .willReturn(List.of(webhook));

        service.deliver(9L, "BOOKING_CANCELLED", new Payload(500L, "ABC123"));

        ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        assertThat(captor.getValue().isSuccess()).isFalse();
        assertThat(captor.getValue().getResponseStatus()).isEqualTo(304);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
