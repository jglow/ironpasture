package com.broadcom.demo.ironpasture.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/inspector")
    public String inspector() {
        return "inspector";
    }
}
