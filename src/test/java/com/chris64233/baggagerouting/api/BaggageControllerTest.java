package com.chris64233.baggagerouting.api;

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

/** HTTP 层：建行李、扫描、改签、误装与 400/404/409/422 错误码。 */
@SpringBootTest
@AutoConfigureMockMvc
class BaggageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String CREATE = """
            {
              "tag": "TAG-WEB-1",
              "passengerItinerary": "PNR-WEB-1",
              "passengerName": "李四",
              "segments": [
                {"transportType": "FLIGHT", "carrier": "CA", "flightNumber": "CA1501", "origin": "PEK", "destination": "PVG"},
                {"transportType": "FLIGHT", "carrier": "CA", "flightNumber": "CA1893", "origin": "PVG", "destination": "SZX"}
              ]
            }
            """;

    @Test
    void createScanRebookAndQueryViaHttp() throws Exception {
        mockMvc.perform(post("/api/baggage")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tag").value("TAG-WEB-1"))
                .andExpect(jsonPath("$.state.status").value("AT_LOCATION"))
                .andExpect(jsonPath("$.state.location").value("PEK"));

        // 404
        mockMvc.perform(get("/api/baggage/NO-SUCH-TAG"))
                .andExpect(status().isNotFound());

        // 装载
        mockMvc.perform(post("/api/baggage/TAG-WEB-1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventId":"W-EV-1","type":"LOAD","flightNumber":"CA1501","location":"PEK"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.status").value("ON_SEGMENT"))
                .andExpect(jsonPath("$.state.flightNumber").value("CA1501"));

        // 重复扫描同一外部事件：返回首次结果 replayed=true
        mockMvc.perform(post("/api/baggage/TAG-WEB-1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventId":"W-EV-1","type":"LOAD","flightNumber":"CA1501","location":"PEK"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));

        // 同事件号内容变化：409
        mockMvc.perform(post("/api/baggage/TAG-WEB-1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventId":"W-EV-1","type":"LOAD","flightNumber":"CA9999","location":"PEK"}
                                """))
                .andExpect(status().isConflict());

        // 卸载
        mockMvc.perform(post("/api/baggage/TAG-WEB-1/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventId":"W-EV-2","type":"UNLOAD","flightNumber":"CA1501","location":"PVG"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.location").value("PVG"));

        // 在错误地点普通卸载场景之外，改签新尾段
        mockMvc.perform(post("/api/baggage/TAG-WEB-1/rebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderNo":"W-RB-1","reason":"网上改签","segments":[
                                  {"transportType":"FLIGHT","carrier":"MU","flightNumber":"MU5101","origin":"PVG","destination":"SZX"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationNo").value(2))
                .andExpect(jsonPath("$.state.activeGenerationNo").value(2));

        // 查询计划路线
        mockMvc.perform(get("/api/baggage/TAG-WEB-1/route"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeGenerationNo").value(2))
                .andExpect(jsonPath("$.generations.length()").value(2))
                .andExpect(jsonPath("$.generations[0].segments[1].status").value("SUPERSEDED"));

        // 查询实际事件
        mockMvc.perform(get("/api/baggage/TAG-WEB-1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(2));
    }

    @Test
    void misloadExceptionChainViaHttp() throws Exception {
        mockMvc.perform(post("/api/baggage")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE.replace("TAG-WEB-1", "TAG-WEB-2")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/baggage/TAG-WEB-2/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventId":"W2-EV-1","type":"LOAD","flightNumber":"CA1501","location":"PEK"}
                                """))
                .andExpect(status().isOk());

        // 误装登记
        mockMvc.perform(post("/api/baggage/TAG-WEB-2/exceptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualFlightNumber":"CA8888","foundAtLocation":"HGH","reason":"错装","continuationSegments":[
                                  {"transportType":"FLIGHT","carrier":"MU","flightNumber":"MU1","origin":"HGH","destination":"SZX"}
                                ]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.action").value("EXCEPTION_UNLOAD"))
                .andExpect(jsonPath("$.generationNo").value(2))
                .andExpect(jsonPath("$.state.location").value("HGH"));

        mockMvc.perform(get("/api/baggage/TAG-WEB-2/exceptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseCount").value(1))
                .andExpect(jsonPath("$.cases[0].caseNo").value("TAG-WEB-2-EX-001"))
                .andExpect(jsonPath("$.cases[0].previousCaseNo").doesNotExist());
    }

    @Test
    void validationAndBusinessErrorsReturnExpectedStatus() throws Exception {
        String create3 = CREATE.replace("TAG-WEB-1", "TAG-WEB-3");

        // 400 参数缺失
        mockMvc.perform(post("/api/baggage")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/baggage")
                        .contentType(MediaType.APPLICATION_JSON).content(create3))
                .andExpect(status().isCreated());
        // 重复标签 409
        mockMvc.perform(post("/api/baggage")
                        .contentType(MediaType.APPLICATION_JSON).content(create3))
                .andExpect(status().isConflict());

        // 装载到错误航班：422 业务规则
        mockMvc.perform(post("/api/baggage/TAG-WEB-3/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalEventId":"W3-EV-BAD","type":"LOAD","flightNumber":"CA9999","location":"PEK"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }
}
