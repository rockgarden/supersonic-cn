package com.tencent.supersonic.headless.chat.parser;

import com.tencent.supersonic.common.config.ParameterConfig;
import com.tencent.supersonic.common.pojo.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Router configuration for Text2SQL query routing decisions.
 * This class provides configurable thresholds and parameters for determining
 * whether a query should be routed to Rule-Based or LLM-based processing.
 */
@Service("HeadlessRouterConfig")
@Slf4j
public class RouterConfig extends ParameterConfig {

    /**
     * Rule confidence threshold - queries with rule confidence below this value will be routed to LLM
     */
    public static final Parameter ROUTER_RULE_CONFIDENCE_THRESHOLD =
            new Parameter("s2.router.rule.confidence.threshold", "0.6", 
                    "规则置信度阈值", 
                    "当规则引擎解析的置信度低于该阈值时，查询将路由到LLM处理", 
                    "number", "路由决策配置");

    /**
     * Schema coverage threshold - minimum ratio of schema elements that must be matched for rule-based processing
     */
    public static final Parameter ROUTER_SCHEMA_COVERAGE_THRESHOLD =
            new Parameter("s2.router.schema.coverage.threshold", "0.5", 
                    "Schema覆盖率阈值", 
                    "当匹配到的Schema元素占查询所需元素的比例低于该阈值时，查询将路由到LLM处理", 
                    "number", "路由决策配置");

    /**
     * Enable dynamic routing based on historical LLM success rate
     */
    public static final Parameter ROUTER_DYNAMIC_ROUTING_ENABLE =
            new Parameter("s2.router.dynamic.routing.enable", "true", 
                    "是否启用动态路由", 
                    "根据数据集历史LLM成功率动态调整路由策略", 
                    "bool", "路由决策配置");

    /**
     * Historical LLM success rate threshold - if success rate is below this, prefer rule-based
     */
    public static final Parameter ROUTER_LLM_SUCCESS_RATE_THRESHOLD =
            new Parameter("s2.router.llm.success.rate.threshold", "0.7", 
                    "LLM历史成功率阈值", 
                    "当数据集历史LLM解析成功率低于该阈值时，优先使用规则引擎", 
                    "number", "路由决策配置");

    /**
     * Minimum rule match score for rule-based processing
     */
    public static final Parameter ROUTER_MIN_RULE_MATCH_SCORE =
            new Parameter("s2.router.min.rule.match.score", "0.3", 
                    "最小规则匹配分数", 
                    "规则匹配的最低分数要求，低于此值则不考虑规则引擎", 
                    "number", "路由决策配置");

    /**
     * Enable circuit breaker for LLM calls
     */
    public static final Parameter ROUTER_LLM_CIRCUIT_BREAKER_ENABLE =
            new Parameter("s2.router.llm.circuit.breaker.enable", "true", 
                    "是否启用LLM熔断机制", 
                    "当LLM调用失败率达到阈值时，自动切换到规则引擎", 
                    "bool", "路由决策配置");

    /**
     * LLM circuit breaker failure threshold
     */
    public static final Parameter ROUTER_LLM_CIRCUIT_BREAKER_FAILURE_THRESHOLD =
            new Parameter("s2.router.llm.circuit.breaker.failure.threshold", "5", 
                    "LLM熔断失败次数阈值", 
                    "连续失败次数达到该阈值时触发熔断", 
                    "number", "路由决策配置");

    /**
     * LLM circuit breaker recovery timeout (in seconds)
     */
    public static final Parameter ROUTER_LLM_CIRCUIT_BREAKER_TIMEOUT =
            new Parameter("s2.router.llm.circuit.breaker.timeout", "60", 
                    "LLM熔断恢复超时时间(秒)", 
                    "熔断后等待该时间尝试恢复LLM调用", 
                    "number", "路由决策配置");

    /**
     * Debug logging level for router decisions
     */
    public static final Parameter ROUTER_DEBUG_LOG_LEVEL =
            new Parameter("s2.router.debug.log.level", "INFO", 
                    "路由决策日志级别", 
                    "控制路由决策相关日志的详细程度：DEBUG/INFO/WARN", 
                    "string", "路由决策配置");

    @Override
    public List<Parameter> getSysParameters() {
        return java.util.Arrays.asList(
                ROUTER_RULE_CONFIDENCE_THRESHOLD,
                ROUTER_SCHEMA_COVERAGE_THRESHOLD,
                ROUTER_DYNAMIC_ROUTING_ENABLE,
                ROUTER_LLM_SUCCESS_RATE_THRESHOLD,
                ROUTER_MIN_RULE_MATCH_SCORE,
                ROUTER_LLM_CIRCUIT_BREAKER_ENABLE,
                ROUTER_LLM_CIRCUIT_BREAKER_FAILURE_THRESHOLD,
                ROUTER_LLM_CIRCUIT_BREAKER_TIMEOUT,
                ROUTER_DEBUG_LOG_LEVEL
        );
    }
}
