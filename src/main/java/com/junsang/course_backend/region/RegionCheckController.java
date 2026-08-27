package com.junsang.course_backend.region;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RegionCheckController {

    @GetMapping("/check-region/seoul")
    public String seoul() {
        return "forward:/check-region/seoul.html";
    }
}
