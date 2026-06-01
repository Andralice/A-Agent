package com.start.agent.controller;

import com.start.agent.service.WritingKnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
public class KnowledgeController {

    @Autowired
    private WritingKnowledgeService writingKnowledgeService;

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam(required = false) String table,
                                             @RequestParam String query) {
        return writingKnowledgeService.search(table, query);
    }

    @GetMapping("/tables")
    public List<String> tables() {
        return List.of("题材与调性推理", "裁决规则", "人设与关系", "写作技法", "命名规则", "场景写法", "桥段套路", "爽点与节奏", "金手指与设定");
    }
}
