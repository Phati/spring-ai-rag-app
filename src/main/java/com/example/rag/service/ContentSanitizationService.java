package com.example.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@Slf4j
public class ContentSanitizationService {

    // Patterns for cleaning text
    private static final Pattern MULTIPLE_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^\\w\\s\\-.,!?;:()'\"\\n]");
    private static final Pattern EXCESSIVE_PUNCTUATION = Pattern.compile("[.]{3,}|[!]{2,}|[?]{2,}");
    private static final Pattern EMPTY_LINES = Pattern.compile("\\n\\s*\\n\\s*\\n");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?[1-9]\\d{1,14}|\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}");

    /**
     * Sanitize and clean document content for better embedding quality
     */
    public String sanitizeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }

        log.debug("Starting content sanitization, original length: {}", content.length());

        // 1. Decode HTML entities
        content = StringEscapeUtils.unescapeHtml4(content);

        // 2. Remove excessive whitespace
        content = MULTIPLE_WHITESPACE.matcher(content).replaceAll(" ");

        // 3. Remove special characters but keep important punctuation
        content = SPECIAL_CHARS.matcher(content).replaceAll("");

        // 4. Fix excessive punctuation
        content = EXCESSIVE_PUNCTUATION.matcher(content).replaceAll("...");

        // 5. Clean up line breaks - remove excessive empty lines
        content = EMPTY_LINES.matcher(content).replaceAll("\n\n");

        // 6. Remove very short lines (likely formatting artifacts)
        content = removeShortLines(content);

        // 7. Remove headers/footers patterns
        content = removeHeaderFooterPatterns(content);

        // 8. Normalize spacing around punctuation
        content = normalizePunctuation(content);

        // 9. Remove personal information (optional - for privacy)
        content = redactPersonalInfo(content);

        // 10. Final cleanup
        content = content.trim();

        log.debug("Content sanitization complete, final length: {}", content.length());

        return content;
    }

    /**
     * Remove lines that are too short to be meaningful content
     */
    private String removeShortLines(String content) {
        StringBuilder cleaned = new StringBuilder();
        String[] lines = content.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            // Keep lines that are longer than 10 characters or contain numbers/important punctuation
            if (trimmed.length() > 10 || 
                trimmed.matches(".*\\d.*") || 
                trimmed.matches(".*[.!?:].*")) {
                cleaned.append(line).append("\n");
            }
        }

        return cleaned.toString();
    }

    /**
     * Remove common header/footer patterns
     */
    private String removeHeaderFooterPatterns(String content) {
        // Remove page numbers
        content = content.replaceAll("(?m)^\\s*Page \\d+.*$", "");
        content = content.replaceAll("(?m)^\\s*\\d+\\s*$", "");
        
        // Remove common footer patterns
        content = content.replaceAll("(?m)^\\s*Copyright.*$", "");
        content = content.replaceAll("(?m)^\\s*©.*$", "");
        content = content.replaceAll("(?m)^\\s*Confidential.*$", "");
        content = content.replaceAll("(?m)^\\s*www\\..*$", "");
        
        return content;
    }

    /**
     * Normalize spacing around punctuation
     */
    private String normalizePunctuation(String content) {
        // Fix spacing around punctuation
        content = content.replaceAll("\\s+([,.!?;:])", "$1");
        content = content.replaceAll("([,.!?;:])([A-Za-z])", "$1 $2");
        content = content.replaceAll("\\s+", " ");
        
        return content;
    }

    /**
     * Redact personal information for privacy (optional)
     */
    private String redactPersonalInfo(String content) {
        // Replace emails with [EMAIL]
        content = EMAIL_PATTERN.matcher(content).replaceAll("[EMAIL]");
        
        // Replace URLs with [URL]
        content = URL_PATTERN.matcher(content).replaceAll("[URL]");
        
        // Replace phone numbers with [PHONE]
        content = PHONE_PATTERN.matcher(content).replaceAll("[PHONE]");
        
        return content;
    }

    /**
     * Extract clean text content from various document types
     */
    public String extractAndCleanText(String rawContent, String contentType) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return "";
        }

        String cleanContent = rawContent;

        // Content type specific cleaning
        if (contentType != null) {
            switch (contentType.toLowerCase()) {
                case "application/pdf":
                case "text/plain":
                    // Basic text cleaning
                    cleanContent = sanitizeContent(rawContent);
                    break;
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                case "application/msword":
                    // Word document specific cleaning
                    cleanContent = cleanWordContent(rawContent);
                    break;
                case "text/html":
                case "application/xhtml+xml":
                    // HTML specific cleaning
                    cleanContent = cleanHtmlContent(rawContent);
                    break;
                default:
                    cleanContent = sanitizeContent(rawContent);
            }
        } else {
            cleanContent = sanitizeContent(rawContent);
        }

        return cleanContent;
    }

    /**
     * Clean Word document content
     */
    private String cleanWordContent(String content) {
        // Remove Word-specific artifacts
        content = content.replaceAll("_Toc\\d+", ""); // Table of contents references
        content = content.replaceAll("_Ref\\d+", ""); // Reference markers
        content = content.replaceAll("\\{[^}]*\\}", ""); // Field codes
        
        return sanitizeContent(content);
    }

    /**
     * Clean HTML content
     */
    private String cleanHtmlContent(String content) {
        // Remove HTML tags (basic approach - Tika should handle this)
        content = content.replaceAll("<[^>]+>", "");
        
        // Remove HTML entities
        content = StringEscapeUtils.unescapeHtml4(content);
        
        return sanitizeContent(content);
    }
}