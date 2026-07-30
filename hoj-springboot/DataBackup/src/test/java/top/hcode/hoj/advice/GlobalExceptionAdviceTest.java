package top.hcode.hoj.advice;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionAdviceTest {

    @Test
    void reportsInvalidIntegerParameterAsBadRequest() throws Exception {
        MockMvc mockMvc = standaloneSetup(new IntegerParameterController())
                .setControllerAdvice(new GlobalExceptionAdvice())
                .build();

        mockMvc.perform(get("/integer-parameter").param("type", "OI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.msg").value("Invalid value for parameter: type"));
    }

    @RestController
    private static class IntegerParameterController {

        @GetMapping("/integer-parameter")
        Integer parse(@RequestParam Integer type) {
            return type;
        }
    }
}
