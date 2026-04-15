package com.broadcom.demo.ironpasture.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("currentPage", "dashboard");
        return "index";
    }

    @GetMapping("/inspector")
    public String inspector(Model model) {
        model.addAttribute("currentPage", "inspector");
        return "inspector";
    }

    @GetMapping("/audit")
    public String audit(Model model) {
        model.addAttribute("currentPage", "audit");
        return "audit";
    }
}
