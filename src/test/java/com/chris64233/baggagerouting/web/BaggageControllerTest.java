package com.chris64233.baggagerouting.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BaggageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static String registerBody(String tag) {
        return """
                {
                  "tag": "%s",
                  "passengerName": "王五",
                  "itineraryRef": "ITIN-WEB",
                  "initialLocation": "A",
                  "segments": [
                    {"flightNo": "F1", "origin": "A", "destination": "B"},
                    {"flightNo": "F2", "origin": "B", "destination": "C"}
                  ]
                }
                """.formatted(tag);
    }

    @Test
    void fullWorkflowThroughHttp() throws Exception {
        // 登记
        mockMvc.perform(post("/api/baggage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("WEB001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tag").value("WEB001"))
                .andExpect(jsonPath("$.locationCode").value("A"))
                .andExpect(jsonPath("$.currentSegmentId").doesNotExist());

        // 装载
        mockMvc.perform(post("/api/baggage/WEB001/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventRef":"E1","type":"LOAD","flightNo":"F1",
                                 "locationCode":"A","occurredAt":"2026-09-27T08:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("LOAD"));

        mockMvc.perform(get("/api/baggage/WEB001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationCode").doesNotExist())
                .andExpect(jsonPath("$.currentSegmentId").isNumber());

        // 重复扫描：返回首次结果
        mockMvc.perform(post("/api/baggage/WEB001/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventRef":"E1","type":"LOAD","flightNo":"F1",
                                 "locationCode":"A","occurredAt":"2026-09-27T08:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalEventRef").value("E1"));

        // 内容变化：409
        mockMvc.perform(post("/api/baggage/WEB001/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventRef":"E1","type":"LOAD","flightNo":"F2",
                                 "locationCode":"A","occurredAt":"2026-09-27T08:00:00Z"}
                                """))
                .andExpect(status().isConflict());

        // 卸载到 B
        mockMvc.perform(post("/api/baggage/WEB001/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventRef":"E2","type":"UNLOAD","flightNo":"F1",
                                 "locationCode":"B","occurredAt":"2026-09-27T09:00:00Z"}
                                """))
                .andExpect(status().isOk());

        // 改签
        mockMvc.perform(post("/api/baggage/WEB001/rebooking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changeNo":"CHG-1","segments":[
                                  {"flightNo":"F5","origin":"B","destination":"D"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("REBOOK"))
                .andExpect(jsonPath("$.changeNo").value("CHG-1"))
                .andExpect(jsonPath("$.segments[0].status").value("PLANNED"));

        // 当前路线为新路线；历史两条，旧路线已取消
        mockMvc.perform(get("/api/baggage/WEB001/route"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("REBOOK"));

        mockMvc.perform(get("/api/baggage/WEB001/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"));

        // 误装
        mockMvc.perform(post("/api/baggage/WEB001/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventRef":"E3","type":"LOAD","flightNo":"F5",
                                 "locationCode":"B","occurredAt":"2026-09-27T10:00:00Z"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/baggage/WEB001/misload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventRef":"E-MIS","foundLocationCode":"X","segments":[
                                  {"flightNo":"F7","origin":"X","destination":"C"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("MISLOAD"))
                .andExpect(jsonPath("$.status").value("RECOVERY_PLANNED"))
                .andExpect(jsonPath("$.foundLocationCode").value("X"));

        mockMvc.perform(get("/api/baggage/WEB001/exceptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/baggage/WEB001/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[3].type").value("EXCEPTION_UNLOAD"));
    }

    @Test
    void notFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/baggage/NOPE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        mockMvc.perform(post("/api/baggage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void businessViolationReturns422() throws Exception {
        mockMvc.perform(post("/api/baggage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("WEB002")))
                .andExpect(status().isCreated());

        // 尚未装载就上报卸载
        mockMvc.perform(post("/api/baggage/WEB002/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventRef":"EX","type":"UNLOAD","flightNo":"F1",
                                 "locationCode":"B","occurredAt":"2026-09-27T08:00:00Z"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }
}
