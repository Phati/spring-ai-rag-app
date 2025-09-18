import com.example.rag.service.ContentSanitizationService;

public class DebugSanitization {
    public static void main(String[] args) {
        ContentSanitizationService service = new ContentSanitizationService();
        String content = "Contact us at support@company.com or admin@example.org";
        String cleaned = service.sanitizeContent(content);
        System.out.println("Original: " + content);
        System.out.println("Cleaned: " + cleaned);
        System.out.println("Contains [EMAIL]: " + cleaned.contains("[EMAIL]"));
    }
}
