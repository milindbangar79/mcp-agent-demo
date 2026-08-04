package com.milind.researchmcp.api;

import com.milind.researchmcp.domain.LearningResourcesService;
import com.milind.researchmcp.domain.LearningResourcesService.LearningResources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ResearchMcpTools {

    private static final Logger log = LoggerFactory.getLogger(ResearchMcpTools.class);

    private final LearningResourcesService learningResourcesService;

    public ResearchMcpTools(LearningResourcesService learningResourcesService) {
        this.learningResourcesService = learningResourcesService;
    }

    @McpTool(
            name = "search_learning_resources",
            description = "Returns curated online calculators, reference articles, and training content "
                    + "for a given financial topic. Call this alongside any calculation tool to enrich "
                    + "the response with learning recommendations.")
    public LearningResources searchLearningResources(
            @McpToolParam(description = "The financial topic to find resources for, e.g. 'simple interest'", required = true)
            String topic) {
        log.info("Tool invoked: search_learning_resources for topic='{}'", topic);
        return learningResourcesService.findResources(topic);
    }
}
