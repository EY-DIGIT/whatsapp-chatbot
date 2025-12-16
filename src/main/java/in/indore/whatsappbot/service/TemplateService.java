package in.indore.whatsappbot.service;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
public class TemplateService {
    private final MessageSource ms;
    public TemplateService(MessageSource ms) { this.ms = ms; }
    public String t(String code, String lang, Object... args) {
        Locale locale = (lang != null && lang.equalsIgnoreCase("hi")) ? new Locale("hi") : Locale.ENGLISH;
        return ms.getMessage(code, args, locale);
    }
}
