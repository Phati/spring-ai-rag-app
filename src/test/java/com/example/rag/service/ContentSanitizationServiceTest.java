package com.example.rag.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContentSanitizationService
 * These tests don't require Spring context or database connectivity
 */
class ContentSanitizationServiceTest {

    @Test
    void testContentSanitization() {
        ContentSanitizationService service = new ContentSanitizationService();
        
        // Test basic sanitization
        String input = "Hello    world!!!   This is a test...  \n\n\n\nWith multiple lines.";
        String output = service.sanitizeContent(input);
        
        assertNotNull(output);
        assertFalse(output.contains("    ")); // Multiple spaces should be removed
        assertTrue(output.length() < input.length()); // Should be shorter after cleaning
        
        // Test empty input
        assertEquals("", service.sanitizeContent(null));
        assertEquals("", service.sanitizeContent(""));
        assertEquals("", service.sanitizeContent("   "));
    }
    
    @Test
    void testFileTypeSpecificCleaning() {
        ContentSanitizationService service = new ContentSanitizationService();
        
        String content = "Sample content with email@test.com and phone 123-456-7890";
        String cleaned = service.extractAndCleanText(content, "text/plain");
        
        assertNotNull(cleaned);
        // Test that content is processed (length changes indicate processing)
        assertTrue(cleaned.length() > 0);
        // The redaction happens in sanitizeContent method
        assertTrue(cleaned.contains("[EMAIL]") || !cleaned.contains("email@test.com")); 
    }
    
    @Test
    void testEmailRedaction() {
        ContentSanitizationService service = new ContentSanitizationService();
        
        String content = "Contact us at support@company.com or admin@example.org";
        String cleaned = service.sanitizeContent(content);
        
        assertNotNull(cleaned);
        // Check that the email redaction worked
        assertTrue(cleaned.contains("[EMAIL]") || (!cleaned.contains("support@company.com") && !cleaned.contains("admin@example.org")));
    }
    
    @Test
    void testPunctuationNormalization() {
        ContentSanitizationService service = new ContentSanitizationService();
        
        String content = "What?????  Really!!!!  Amazing... ... ... end.";
        String cleaned = service.sanitizeContent(content);
        
        assertNotNull(cleaned);
        assertFalse(cleaned.contains("!!!!"));
        assertFalse(cleaned.contains("?????"));
        assertTrue(cleaned.contains("..."));
    }
}