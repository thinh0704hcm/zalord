package zalord.message_service.exception;

public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(String message) {
        super(message);
    }
}
