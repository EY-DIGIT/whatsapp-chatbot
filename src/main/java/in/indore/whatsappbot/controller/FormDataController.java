package in.indore.whatsappbot.controller;

import in.indore.whatsappbot.service.FormDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/")
public class FormDataController {

    private final FormDataService formDataService;

    public FormDataController(FormDataService formDataService) {
        this.formDataService = formDataService;
    }

    @GetMapping("get/form-data/{key}")
    public ResponseEntity<Map<String, Object>> getFormData(@PathVariable("key") String key) {
        try {
            Map<String, Object> data = formDataService.getFormData(key);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve form data", e);
        }
    }
}
