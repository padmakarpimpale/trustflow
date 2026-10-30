package com.padmakar.trustflow.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class PageController {

    @GetMapping({ "/", "/legacy" })
    public String home() {
        return "redirect:/app/index.html";
    }

    @GetMapping("/login")
    public String login() {
        return "forward:/app/index.html";
    }

    @GetMapping("/cases/new")
    public String newCase() {
        return "redirect:/app/index.html#new";
    }

    @GetMapping("/cases/{id}")
    public String caseDetail(@PathVariable Long id) {
        return "redirect:/app/index.html#case/" + id;
    }

    @GetMapping("/notifications")
    public String notifications() {
        return "redirect:/app/index.html#notifications";
    }

    @GetMapping("/admin/policies")
    public String policies() {
        return "redirect:/app/index.html#policies";
    }
}
