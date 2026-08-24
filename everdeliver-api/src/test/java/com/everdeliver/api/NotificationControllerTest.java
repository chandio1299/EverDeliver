package com.everdeliver.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.everdeliver.persistence.NotificationStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    void unknownChannelIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"fax\",\"email\":\"a@b.com\",\"message\":\"hi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultEmailBodyIsAccepted() throws Exception {
        Mockito.when(notificationService.enqueue(Mockito.any()))
                .thenReturn(new NotificationQueuedResponse(UUID.randomUUID(), NotificationStatus.QUEUED));

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"subject\":\"Hi\",\"message\":\"Hello\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void statsPathIsNotCapturedAsId() throws Exception {
        Mockito.when(notificationService.stats(Mockito.any()))
                .thenReturn(NotificationStatsResponse.builder()
                        .total(0)
                        .successRate(0)
                        .byStatus(java.util.Map.of())
                        .byChannel(java.util.Map.of())
                        .failuresByChannel(java.util.Map.of())
                        .build());

        mockMvc.perform(get("/api/v1/notifications/stats")).andExpect(status().isOk());
    }

    @Test
    void retryDelegatesToService() throws Exception {
        UUID id = UUID.randomUUID();
        Mockito.when(notificationService.retry(id))
                .thenReturn(NotificationResponse.builder()
                        .id(id)
                        .status(NotificationStatus.QUEUED)
                        .channel("email")
                        .recipient("user@example.com")
                        .retryCount(1)
                        .build());

        mockMvc.perform(post("/api/v1/notifications/" + id + "/retry")).andExpect(status().isOk());
    }
}
