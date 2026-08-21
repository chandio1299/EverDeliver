package com.everdeliver.api;

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
                .andExpect(status().isOk());
    }
}
