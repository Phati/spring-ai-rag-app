package com.example.rag.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class WebController {

    @GetMapping
    public String index(Model model) {
        model.addAttribute("title", "Spring AI RAG - Document Upload & Chat");
        return "index";
    }
    
    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("title", "Spring AI RAG - Chat Interface");
        return "index";
    }
}