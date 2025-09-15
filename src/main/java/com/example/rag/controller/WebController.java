package com.example.rag.controller;

import com.example.rag.entity.Document;
import com.example.rag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class WebController {

    private final DocumentService documentService;

    @GetMapping
    public String index(Model model) {
        try {
            List<Document> documents = documentService.getAllDocuments();
            model.addAttribute("documents", documents);
            model.addAttribute("documentCount", documents.size());
        } catch (Exception e) {
            model.addAttribute("documents", List.of());
            model.addAttribute("documentCount", 0);
        }
        return "index";
    }

    @GetMapping("/chat")
    public String chat(Model model) {
        try {
            List<Document> documents = documentService.getAllDocuments();
            model.addAttribute("documents", documents);
        } catch (Exception e) {
            model.addAttribute("documents", List.of());
        }
        return "chat";
    }
}