package com.tencent.supersonic.headless.chat.parser;

import com.tencent.supersonic.common.util.ContextUtils;
import com.tencent.supersonic.headless.api.pojo.SchemaElementMatch;
import com.tencent.supersonic.headless.api.pojo.SchemaElementType;
import com.tencent.supersonic.headless.chat.ChatQueryContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * QueryRouter - Core routing decision component for Text2SQL queries.
 * 
 * This class implements the routing logic that determines whether a query should be
 * processed by the Rule-Based engine or the LLM-based engine. The decision is based on
 * multiple factors including:
 * 
 * 1. Rule Confidence Score: How confident the rule engine is about the parse result
 * 2. Schema Coverage: Ratio of matched schema elements to required elements
 * 3. Historical LLM Success Rate: Past performance of LLM for the target dataset
 * 4. Circuit Breaker Status: Whether LLM calls are currently disabled due to failures
 * 
 * All thresholds are configurable via RouterConfig parameters.
 */
@Slf4j
@Data
public class QueryRouter {

    /**
     * Circuit breaker state for LLM calls
     */
    private boolean circuitBreakerOpen = false;
    
    /**
     * Consecutive LLM failure count
     */
    private int consecutiveFailures = 0;
    
    /**
     * Last circuit breaker trigger time
     */
    private long circuitBreakerTriggerTime = 0;

    /**
     * Main routing decision method.
     * 
     * @param chatQueryContext the query context containing mapping results and request info
     * @return RoutingDecision containing the decision and all relevant metrics
     */
    public RoutingDecision decideRoute(ChatQueryContext chatQueryContext) {
        RoutingDecision decision = new RoutingDecision();
        decision.setQueryText(chatQueryContext.getRequest().getQueryText());
        
        // Get configuration parameters
        RouterConfig routerConfig = ContextUtils.getBean(RouterConfig.class);
        double ruleConfidenceThreshold = Double.parseDouble(
                routerConfig.getParameterValue(RouterConfig.ROUTER_RULE_CONFIDENCE_THRESHOLD));
        double schemaCoverageThreshold = Double.parseDouble(
                routerConfig.getParameterValue(RouterConfig.ROUTER_SCHEMA_COVERAGE_THRESHOLD));
        double minRuleMatchScore = Double.parseDouble(
                routerConfig.getParameterValue(RouterConfig.ROUTER_MIN_RULE_MATCH_SCORE));
        boolean dynamicRoutingEnabled = Boolean.parseBoolean(
                routerConfig.getParameterValue(RouterConfig.ROUTER_DYNAMIC_ROUTING_ENABLE));
        double llmSuccessRateThreshold = Double.parseDouble(
                routerConfig.getParameterValue(RouterConfig.ROUTER_LLM_SUCCESS_RATE_THRESHOLD));
        boolean circuitBreakerEnabled = Boolean.parseBoolean(
                routerConfig.getParameterValue(RouterConfig.ROUTER_LLM_CIRCUIT_BREAKER_ENABLE));
        int failureThreshold = Integer.parseInt(
                routerConfig.getParameterValue(RouterConfig.ROUTER_LLM_CIRCUIT_BREAKER_FAILURE_THRESHOLD));
        long recoveryTimeout = Long.parseLong(
                routerConfig.getParameterValue(RouterConfig.ROUTER_LLM_CIRCUIT_BREAKER_TIMEOUT)) * 1000;

        // Calculate rule confidence score
        double ruleConfidence = calculateRuleConfidence(chatQueryContext);
        decision.setRuleConfidence(ruleConfidence);
        
        // Calculate schema coverage
        double schemaCoverage = calculateSchemaCoverage(chatQueryContext);
        decision.setSchemaCoverage(schemaCoverage);
        
        // Check minimum rule match score
        boolean meetsMinScore = ruleConfidence >= minRuleMatchScore;
        decision.setMeetsMinRuleScore(meetsMinScore);
        
        // Get historical LLM success rate for the dataset
        double historicalLlmSuccessRate = getHistoricalLlmSuccessRate(chatQueryContext);
        decision.setHistoricalLlmSuccessRate(historicalLlmSuccessRate);
        
        // Check circuit breaker status
        boolean circuitBreakerAllowsLLM = true;
        if (circuitBreakerEnabled && circuitBreakerOpen) {
            if (System.currentTimeMillis() - circuitBreakerTriggerTime > recoveryTimeout) {
                // Try to recover
                circuitBreakerOpen = false;
                consecutiveFailures = 0;
                log.info("Circuit breaker recovered, allowing LLM calls again");
            } else {
                circuitBreakerAllowsLLM = false;
                log.warn("Circuit breaker is OPEN - LLM calls disabled due to consecutive failures");
            }
        }
        decision.setCircuitBreakerOpen(!circuitBreakerAllowsLLM);
        
        // Make routing decision
        String routeTo;
        String reason;
        
        if (!meetsMinScore) {
            // Rule confidence too low, must use LLM
            routeTo = "LLM";
            reason = String.format("规则置信度 (%.2f) 低于最小阈值 (%.2f)", ruleConfidence, minRuleMatchScore);
        } else if (!circuitBreakerAllowsLLM) {
            // Circuit breaker is open, force rule-based
            routeTo = "RULE";
            reason = "LLM 熔断机制触发，强制使用规则引擎";
        } else if (ruleConfidence >= ruleConfidenceThreshold && schemaCoverage >= schemaCoverageThreshold) {
            // Both thresholds met, use rule-based
            routeTo = "RULE";
            reason = String.format("规则置信度 (%.2f >= %.2f) 和 Schema 覆盖率 (%.2f >= %.2f) 均满足阈值",
                    ruleConfidence, ruleConfidenceThreshold, schemaCoverage, schemaCoverageThreshold);
        } else if (dynamicRoutingEnabled && historicalLlmSuccessRate < llmSuccessRateThreshold) {
            // Dynamic routing: LLM has poor history, prefer rule-based even with lower confidence
            routeTo = "RULE";
            reason = String.format("动态路由：历史 LLM 成功率 (%.2f) 低于阈值 (%.2f)，优先使用规则引擎",
                    historicalLlmSuccessRate, llmSuccessRateThreshold);
        } else {
            // Default to LLM when rule-based doesn't meet criteria
            routeTo = "LLM";
            if (ruleConfidence < ruleConfidenceThreshold) {
                reason = String.format("规则置信度 (%.2f) 低于阈值 (%.2f)", ruleConfidence, ruleConfidenceThreshold);
            } else if (schemaCoverage < schemaCoverageThreshold) {
                reason = String.format("Schema 覆盖率 (%.2f) 低于阈值 (%.2f)", schemaCoverage, schemaCoverageThreshold);
            } else {
                reason = "默认路由到 LLM";
            }
        }
        
        decision.setRouteTo(routeTo);
        decision.setReason(reason);
        
        // Log debug information
        logDebugInfo(decision, routerConfig);
        
        return decision;
    }

    /**
     * Calculate rule confidence score based on schema element matches.
     * 
     * The confidence is calculated as a weighted average of:
     * - Similarity scores of matched elements
     * - Coverage of required element types
     * - Presence of key elements (metrics, dimensions)
     */
    private double calculateRuleConfidence(ChatQueryContext chatQueryContext) {
        if (chatQueryContext.getMapInfo() == null || 
            chatQueryContext.getMapInfo().getDataSetElementMatches() == null) {
            return 0.0;
        }
        
        double totalScore = 0.0;
        int matchCount = 0;
        
        for (List<SchemaElementMatch> matches : 
             chatQueryContext.getMapInfo().getDataSetElementMatches().values()) {
            for (SchemaElementMatch match : matches) {
                if (match.getSimilarity() != null) {
                    // Weight by element type importance
                    double weight = getElementTypeWeight(match.getElement().getType());
                    totalScore += match.getSimilarity() * weight;
                    matchCount++;
                }
            }
        }
        
        if (matchCount == 0) {
            return 0.0;
        }
        
        return totalScore / matchCount;
    }

    /**
     * Calculate schema coverage ratio.
     * 
     * Coverage = (matched elements) / (total available elements in dataset)
     */
    private double calculateSchemaCoverage(ChatQueryContext chatQueryContext) {
        if (chatQueryContext.getMapInfo() == null || 
            chatQueryContext.getMapInfo().getDataSetElementMatches() == null) {
            return 0.0;
        }
        
        int matchedCount = 0;
        int totalCount = 0;
        
        for (Map.Entry<Long, List<SchemaElementMatch>> entry : 
             chatQueryContext.getMapInfo().getDataSetElementMatches().entrySet()) {
            matchedCount += entry.getValue().size();
            
            // Get total elements from semantic schema
            if (chatQueryContext.getSemanticSchema() != null) {
                totalCount += chatQueryContext.getSemanticSchema().getMetrics(entry.getKey()).size();
                totalCount += chatQueryContext.getSemanticSchema().getDimensions(entry.getKey()).size();
            }
        }
        
        if (totalCount == 0) {
            return 0.0;
        }
        
        return (double) matchedCount / totalCount;
    }

    /**
     * Get weight for different element types.
     * Metrics and dimensions are more important than values.
     */
    private double getElementTypeWeight(SchemaElementType type) {
        switch (type) {
            case METRIC:
                return 1.5;  // Most important
            case DIMENSION:
                return 1.2;  // Important
            case DATASET:
                return 1.0;  // Base
            case VALUE:
                return 0.8;  // Less important
            case TERM:
                return 0.5;  // Least important
            default:
                return 1.0;
        }
    }

    /**
     * Get historical LLM success rate for the target dataset.
     * 
     * This is a placeholder implementation. In production, this should query
     * a persistent store of historical parsing results.
     */
    private double getHistoricalLlmSuccessRate(ChatQueryContext chatQueryContext) {
        // TODO: Implement actual historical data lookup
        // For now, return a default value
        return 0.85;  // Default 85% success rate
    }

    /**
     * Record LLM call failure for circuit breaker tracking.
     */
    public void recordLlmFailure() {
        RouterConfig routerConfig = ContextUtils.getBean(RouterConfig.class);
        boolean circuitBreakerEnabled = Boolean.parseBoolean(
                routerConfig.getParameterValue(RouterConfig.ROUTER_LLM_CIRCUIT_BREAKER_ENABLE));
        int failureThreshold = Integer.parseInt(
                routerConfig.getParameterValue(RouterConfig.ROUTER_LLM_CIRCUIT_BREAKER_FAILURE_THRESHOLD));
        
        if (!circuitBreakerEnabled) {
            return;
        }
        
        consecutiveFailures++;
        log.warn("LLM call failed, consecutive failures: {}", consecutiveFailures);
        
        if (consecutiveFailures >= failureThreshold) {
            circuitBreakerOpen = true;
            circuitBreakerTriggerTime = System.currentTimeMillis();
            log.error("Circuit breaker TRIGGERED after {} consecutive failures", consecutiveFailures);
        }
    }

    /**
     * Record LLM call success for circuit breaker tracking.
     */
    public void recordLlmSuccess() {
        if (consecutiveFailures > 0) {
            log.info("LLM call succeeded, resetting failure counter");
            consecutiveFailures = 0;
        }
    }

    /**
     * Log debug information for routing decision.
     */
    private void logDebugInfo(RoutingDecision decision, RouterConfig routerConfig) {
        String logLevel = routerConfig.getParameterValue(RouterConfig.ROUTER_DEBUG_LOG_LEVEL);
        
        if ("DEBUG".equalsIgnoreCase(logLevel)) {
            log.debug("=== ROUTING DECISION DEBUG INFO ===");
            log.debug("Query: {}", decision.getQueryText());
            log.debug("Route To: {}", decision.getRouteTo());
            log.debug("Reason: {}", decision.getReason());
            log.debug("Rule Confidence: {:.4}", decision.getRuleConfidence());
            log.debug("Schema Coverage: {:.4}", decision.getSchemaCoverage());
            log.debug("Meets Min Rule Score: {}", decision.isMeetsMinRuleScore());
            log.debug("Historical LLM Success Rate: {:.4}", decision.getHistoricalLlmSuccessRate());
            log.debug("Circuit Breaker Open: {}", decision.isCircuitBreakerOpen());
            log.debug("===================================");
        } else if ("INFO".equalsIgnoreCase(logLevel)) {
            log.info("Query routed to {}: {} | Confidence={:.3}, Coverage={:.3}",
                    decision.getRouteTo(), decision.getReason(),
                    decision.getRuleConfidence(), decision.getSchemaCoverage());
        }
    }

    /**
     * Inner class representing the routing decision result.
     */
    @Data
    public static class RoutingDecision {
        private String queryText;
        private String routeTo;  // "RULE" or "LLM"
        private String reason;
        private double ruleConfidence;
        private double schemaCoverage;
        private boolean meetsMinRuleScore;
        private double historicalLlmSuccessRate;
        private boolean circuitBreakerOpen;
    }
}
