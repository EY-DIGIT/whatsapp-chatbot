package in.indore.whatsappbot.service;

import in.indore.whatsappbot.model.Feedback;
import in.indore.whatsappbot.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public void saveFeedback(String userRequestId, Integer rating) {
        Feedback feedback = new Feedback(userRequestId, rating);
        feedbackRepository.save(feedback);
    }
}
