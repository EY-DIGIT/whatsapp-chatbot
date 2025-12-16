package in.indore.whatsappbot.service;

import in.indore.whatsappbot.model.Language;
import in.indore.whatsappbot.repository.LanguageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LanguageService {
    private final LanguageRepository repo;
    public LanguageService(LanguageRepository repo) { this.repo = repo; }

    @Transactional
    public void saveLanguage(String phone, String lang) {
        Language u = repo.findById(phone).orElse(new Language(phone, lang));
        u.setLanguage(lang);
        repo.save(u);
    }

    public String getLanguage(String phone) {
        return repo.findById(phone).map(Language::getLanguage).orElse(null);
    }
}
