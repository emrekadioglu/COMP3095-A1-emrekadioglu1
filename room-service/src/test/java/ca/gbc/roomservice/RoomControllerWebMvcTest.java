package ca.gbc.roomservice;

import ca.gbc.roomservice.controller.RoomController;
import ca.gbc.roomservice.dto.RoomRequest;
import ca.gbc.roomservice.dto.RoomResponse;
import ca.gbc.roomservice.model.RoomFeature;
import ca.gbc.roomservice.model.RoomType;
import ca.gbc.roomservice.service.RoomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
class RoomControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoomService roomService;

    @Test
    void createRoom_success() throws Exception {
        RoomRequest request = new RoomRequest("SW-999", "Test Room", "SW", 5, RoomType.STUDY_ROOM, true, Set.of(RoomFeature.WHITEBOARD));
        RoomResponse response = new RoomResponse(1L, "SW-999", "Test Room", "SW", 5, RoomType.STUDY_ROOM, true, Set.of(RoomFeature.WHITEBOARD), true);
        when(roomService.createRoom(any())).thenReturn(response);

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SW-999"));
    }

    @Test
    void createRoom_validationFailure() throws Exception {
        RoomRequest request = new RoomRequest("", "", "", 0, null, false, null);

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
